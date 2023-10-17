package it.pagopa.interop.attributeregistrymanagement

import akka.actor
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ActorTestKitBase}
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.cluster.typed.{Cluster, Join}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{Marshal, ToEntityMarshaller}
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.RouteResult.routeToFunction
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal, Unmarshaller}
import akka.util.Timeout
import cats.implicits._
import it.pagopa.interop.attributeregistrymanagement.api.impl._
import it.pagopa.interop.attributeregistrymanagement.api.{
  AttributeApi,
  AttributeApiMarshaller,
  AttributeApiService,
  HealthApi
}
import it.pagopa.interop.attributeregistrymanagement.model.AttributeKind._
import it.pagopa.interop.attributeregistrymanagement.model.persistence._
import it.pagopa.interop.attributeregistrymanagement.model.{Attribute, AttributeSeed, AttributesResponse, Problem}
import it.pagopa.interop.attributeregistrymanagement.server.Controller
import it.pagopa.interop.attributeregistrymanagement.server.impl.Main.behaviorFactory
import it.pagopa.interop.commons.utils.AkkaUtils
import it.pagopa.interop.commons.utils.AkkaUtils.getShard
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import munit.{FutureFixture, ScalaCheckSuite}
import org.scalacheck.Gen

import java.net.InetAddress
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext, Future}

trait AkkaTestSuite extends ScalaCheckSuite {

  override def munitFixtures = List(actorSystem)

  val actorSystem: FutureFixture[ActorSystem[Nothing]] = new FutureFixture[ActorSystem[Nothing]]("Actor System") {

    var testkit: ActorTestKit = null

    def apply(): ActorSystem[Nothing] = testkit.system

    override def beforeAll(): Future[Unit] = {
      testkit = ActorTestKit(ActorTestKitBase.testNameFromCallStack(), AkkaTestConfiguration.config)

      implicit val classicSystem: actor.ActorSystem = testkit.system.classicSystem
      implicit val ec: ExecutionContext             = testkit.system.executionContext

      val cluster: Cluster          = Cluster(testkit.system)
      cluster.manager ! Join(cluster.selfMember.address)
      val sharding: ClusterSharding = ClusterSharding(testkit.system)

      val persistentEntity: Entity[Command, ShardingEnvelope[Command]] =
        Entity(AttributePersistentBehavior.TypeKey)(behaviorFactory)

      sharding.init(persistentEntity)

      val wrappingDirective = SecurityDirectives.authenticateOAuth2("SecurityRealm", AdminMockAuthenticator)

      val uuidSupplier: UUIDSupplier                 = () => UUID.randomUUID()
      val timeSupplier: OffsetDateTimeSupplier       = () => OffsetDateTime.now()
      val partyApiMarshaller: AttributeApiMarshaller = AttributeApiMarshallerImpl

      val healthApi: HealthApi = new HealthApi(
        new HealthServiceApiImpl(),
        new HealthApiMarshallerImpl(),
        SecurityDirectives.authenticateOAuth2("SecurityRealm", AkkaUtils.PassThroughAuthenticator)
      )

      val partyApiService: AttributeApiService =
        new AttributeApiServiceImpl(uuidSupplier, timeSupplier, testkit.system, sharding, persistentEntity)(
          testkit.system.executionContext
        )

      val attributeApi: AttributeApi = new AttributeApi(partyApiService, partyApiMarshaller, wrappingDirective)

      val controller = new Controller(attributeApi, healthApi)
      Http().newServerAt("0.0.0.0", 8088).bind(controller.routes).void
    }

    override def beforeEach(context: BeforeEach): Future[Unit] = {
      implicit val as: ActorSystem[Nothing] = testkit.system
      implicit val ec: ExecutionContext     = as.executionContext
      for {
        (getAllStatus, attributes) <- getAllAttributes
        ids = attributes.map(_.id)
        deletionStatuses               <- Future.traverse(ids)(deleteAttribute).map(_.toSet)
        (afterStatus, afterAttributes) <- getAllAttributes
      } yield {
        assertEquals(getAllStatus, OK)
        if (attributes.nonEmpty) assertEquals(deletionStatuses, Set[StatusCode](NoContent))
        else assertEquals(deletionStatuses, Set[StatusCode]())
        assertEquals(afterStatus, OK)
        assertEquals(afterAttributes.size, 0)
      }
    }

    override def afterAll(): Future[Unit] = Future.successful(testkit.shutdownTestKit())
  }

  def await[T](future: Future[T]): T = Await.result(future, Duration.Inf)

  private val requestHeaders: Seq[HttpHeader] = Seq(
    headers.Authorization(OAuth2BearerToken("token")),
    headers.RawHeader("X-Correlation-Id", "test-id"),
    headers.`X-Forwarded-For`(RemoteAddress(InetAddress.getByName("127.0.0.1")))
  )

  implicit def toEntityMarshallerAttributeSeed: ToEntityMarshaller[AttributeSeed] = sprayJsonMarshaller[AttributeSeed]
  implicit def fromEntityUnmarshallerAttribute: FromEntityUnmarshaller[Attribute] = sprayJsonUnmarshaller[Attribute]
  implicit def fromEntityUnmarshallerProblem: FromEntityUnmarshaller[Problem]     = sprayJsonUnmarshaller[Problem]

  def createAttribute[T](
    seed: AttributeSeed
  )(implicit actorSystem: ActorSystem[_], mf: Unmarshaller[ResponseEntity, T]): Future[(StatusCode, T)] = {
    implicit val ec: ExecutionContext = actorSystem.executionContext
    for {
      data     <- Marshal(seed).to[MessageEntity].map(_.dataBytes)
      response <- execute("attributes", POST, HttpEntity(ContentTypes.`application/json`, data))
      body     <- Unmarshal(response.entity).to[T]
    } yield (response.status, body)
  }

  def getAllAttributes(implicit actorSystem: ActorSystem[_]): Future[(StatusCode, List[Attribute])] = {
    implicit val ec: ExecutionContext = actorSystem.executionContext
    for {
      response <- execute("attributes", GET)
      body     <- Unmarshal(response.entity).to[AttributesResponse]
    } yield (response.status, body.attributes.toList)
  }

  def deleteAttribute(attributeId: UUID)(implicit actorSystem: ActorSystem[_]): Future[StatusCode] = {
    implicit val ec: ExecutionContext = actorSystem.executionContext
    implicit val timeout: Timeout     = 300.seconds

    val attributeIdString    = attributeId.toString
    val result: Future[Unit] =
      commander(attributeIdString).askWithStatus(ref => DeleteAttribute(attributeIdString, ref))

    result
      .map(_ => StatusCodes.NoContent)
      .recover(_ => StatusCodes.InternalServerError)
  }

  private def commander(id: String)(implicit actorSystem: ActorSystem[_]): EntityRef[Command] = {
    val sharding: ClusterSharding                                    = ClusterSharding(actorSystem)
    val persistentEntity: Entity[Command, ShardingEnvelope[Command]] =
      Entity(AttributePersistentBehavior.TypeKey)(behaviorFactory)
    val settings: ClusterShardingSettings = persistentEntity.settings.getOrElse(ClusterShardingSettings(actorSystem))
    sharding.entityRefFor(AttributePersistentBehavior.TypeKey, getShard(id, settings.numberOfShards))
  }

  def findAttributeByName(name: String)(implicit actorSystem: ActorSystem[_]): Future[(StatusCode, Attribute)] = {
    implicit val ec: ExecutionContext = actorSystem.executionContext
    for {
      response <- execute(s"attributes/name/$name", GET)
      body     <- Unmarshal(response.entity).to[Attribute]
    } yield (response.status, body)
  }

  def findAttributeByOriginAndCode(origin: String, code: String)(implicit
    actorSystem: ActorSystem[_]
  ): Future[(StatusCode, Attribute)] = {
    implicit val ec: ExecutionContext = actorSystem.executionContext
    for {
      response <- execute(s"attributes/origin/$origin/code/$code", GET)
      body     <- Unmarshal(response.entity).to[Attribute]
    } yield (response.status, body)
  }

  private def execute(path: String, verb: HttpMethod, data: RequestEntity)(implicit
    actorSystem: ActorSystem[_]
  ): Future[HttpResponse] = Http().singleRequest(
    HttpRequest(
      uri = s"${AkkaTestConfiguration.serviceURL}/$path",
      method = verb,
      entity = data,
      headers = requestHeaders
    )
  )

  private def execute(path: String, verb: HttpMethod)(implicit actorSystem: ActorSystem[_]): Future[HttpResponse] =
    Http().singleRequest(
      HttpRequest(uri = s"${AkkaTestConfiguration.serviceURL}/$path", method = verb, headers = requestHeaders)
    )

  private val nameGenerator: Gen[String] = for {
    nOfLetters <- Gen.chooseNum(5, 30)
    name       <- Gen.stringOfN(nOfLetters, Gen.alphaChar)
  } yield name

  private val descriptionGenerator: Gen[String] = for {
    nOfLetters <- Gen.chooseNum(5, 50)
    name       <- Gen.stringOfN(nOfLetters, Gen.alphaChar)
  } yield name

  private val originGenerator: Gen[Option[String]] = Gen.stringOfN(3, Gen.alphaUpperChar).map(Option(_))

  val attribute: Gen[Attribute] = for {
    uuid        <- Gen.uuid
    code        <- Gen.chooseNum(100, 50000).map(_.toString).map(Option(_))
    kind        <- Gen.oneOf(CERTIFIED, DECLARED, VERIFIED)
    description <- descriptionGenerator
    origin      <- originGenerator
    name        <- nameGenerator
  } yield Attribute(uuid, code, kind, description, origin, name, OffsetDateTime.now())

  val attributesSeed: Gen[AttributeSeed] = for {
    code        <- Gen.chooseNum(100, 50000).map(_.toString).map(Option(_))
    kind        <- Gen.oneOf(CERTIFIED, DECLARED, VERIFIED)
    description <- descriptionGenerator
    origin      <- originGenerator
    name        <- nameGenerator
  } yield AttributeSeed(code, kind, description, origin, name)

  val attributeAndSeed: Gen[(Attribute, AttributeSeed)] = for {
    uuid        <- Gen.uuid
    code        <- Gen.chooseNum(100, 50000).map(_.toString).map(Option(_))
    kind        <- Gen.oneOf(CERTIFIED, DECLARED, VERIFIED)
    description <- descriptionGenerator
    origin      <- originGenerator
    name        <- nameGenerator
  } yield (
    Attribute(uuid, code, kind, description, origin, name, OffsetDateTime.now()),
    AttributeSeed(code, kind, description, origin, name)
  )

  def attributesTestCase(n: Int): Gen[(List[AttributeSeed], List[Attribute])] =
    Gen
      .containerOfN[List, (Attribute, AttributeSeed)](n, attributeAndSeed)
      .map(_.distinctBy(_._2.name))
      .map(list => (list.map(_._2), list.map(_._1)))

}

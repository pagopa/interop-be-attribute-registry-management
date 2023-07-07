package it.pagopa.interop.attributeregistrymanagement

import akka.actor
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.interop.attributeregistrymanagement.api._
import it.pagopa.interop.attributeregistrymanagement.api.impl._
import it.pagopa.interop.attributeregistrymanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.attributeregistrymanagement.model._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.{AttributePersistentBehavior, Command}
import it.pagopa.interop.attributeregistrymanagement.server.Controller
import it.pagopa.interop.attributeregistrymanagement.server.impl.Dependencies
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import org.scalamock.scalatest.MockFactory
import spray.json._

import java.net.InetAddress
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

trait ItSpecHelper
    extends ItSpecConfiguration
    with ItCqrsSpec
    with MockFactory
    with SprayJsonSupport
    with DefaultJsonProtocol
    with Dependencies {
  self: ScalaTestWithActorTestKit =>

  val bearerToken: String                   = "token"
  final val requestHeaders: Seq[HttpHeader] =
    Seq(
      headers.Authorization(OAuth2BearerToken("token")),
      headers.RawHeader("X-Correlation-Id", "test-id"),
      headers.`X-Forwarded-For`(RemoteAddress(InetAddress.getByName("127.0.0.1")))
    )

  val mockUUIDSupplier: UUIDSupplier           = mock[UUIDSupplier]
  val mockTimeSupplier: OffsetDateTimeSupplier = mock[OffsetDateTimeSupplier]
  val mockPartyRegistry: PartyRegistryService  = mock[PartyRegistryService]

  val healthApiMock: HealthApi = mock[HealthApi]

  val apiMarshaller: AttributeApiMarshaller = AttributeApiMarshallerImpl

  var controller: Option[Controller]                 = None
  var bindServer: Option[Future[Http.ServerBinding]] = None

  val wrappingDirective: AuthenticationDirective[Seq[(String, String)]] =
    SecurityDirectives.authenticateOAuth2("SecurityRealm", AdminMockAuthenticator)

  val sharding: ClusterSharding = ClusterSharding(system)

  val httpSystem: ActorSystem[Any]                        =
    ActorSystem(Behaviors.ignore[Any], name = system.name, config = system.settings.config)
  implicit val executionContext: ExecutionContextExecutor = httpSystem.executionContext
  val classicSystem: actor.ActorSystem                    = httpSystem.classicSystem

  override def startServer(): Unit = {
    val persistentEntity: Entity[Command, ShardingEnvelope[Command]] =
      Entity(AttributePersistentBehavior.TypeKey)(behaviorFactory)

    Cluster(system).manager ! Join(Cluster(system).selfMember.address)
    sharding.init(persistentEntity)

    val attributeApi =
      new AttributeApi(
        new AttributeApiServiceImpl(
          mockUUIDSupplier,
          mockTimeSupplier,
          system,
          sharding,
          persistentEntity,
          mockPartyRegistry
        ),
        apiMarshaller,
        wrappingDirective
      )

    if (ApplicationConfiguration.projectionsEnabled) initProjections()

    controller = Some(new Controller(attributeApi, healthApiMock)(classicSystem))

    controller foreach { controller =>
      bindServer = Some(
        Http()(classicSystem)
          .newServerAt("0.0.0.0", 18088)
          .bind(controller.routes)
      )

      Await.result(bindServer.get, 100.seconds)
    }
  }

  override def shutdownServer(): Unit = {
    bindServer.foreach(_.foreach(_.unbind()))
    ActorTestKit.shutdown(httpSystem, 5.seconds)
  }

  def createAttribute(attributeId: UUID): Attribute = {
    (() => mockUUIDSupplier.get()).expects().returning(attributeId).once()
    (() => mockTimeSupplier.get()).expects().returning(OffsetDateTime.now).once()

    val seed = AttributeSeed(
      code = Some("code"),
      kind = AttributeKind.CERTIFIED,
      description = "Attribute description",
      origin = Some("AttrOrigin"),
      name = s"AttrName-${UUID.randomUUID()}"
    )

    val data = seed.toJson.compactPrint

    val response = request(s"$serviceURL/attributes", HttpMethods.POST, Some(data))

    response.status shouldBe StatusCodes.Created

    Await.result(Unmarshal(response).to[Attribute], Duration.Inf)
  }

  def deleteAttribute(attributeId: UUID): Unit = {
    val response =
      request(s"$serviceURL/attributes/$attributeId", HttpMethods.DELETE)
    response.status shouldBe StatusCodes.NoContent
    ()
  }

  def request(uri: String, method: HttpMethod, data: Option[String] = None): HttpResponse = {
    val httpRequest: HttpRequest = HttpRequest(uri = uri, method = method, headers = requestHeaders)

    val requestWithEntity: HttpRequest =
      data.fold(httpRequest)(d => httpRequest.withEntity(HttpEntity(ContentTypes.`application/json`, d)))

    Await.result(Http().singleRequest(requestWithEntity), Duration.Inf)
  }
}

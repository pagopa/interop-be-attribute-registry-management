package it.pagopa.interop.attributeregistrymanagement

import akka.actor
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.directives.SecurityDirectives
import it.pagopa.interop.attributeregistrymanagement.api.impl.{AttributeApiMarshallerImpl, AttributeApiServiceImpl}
import it.pagopa.interop.attributeregistrymanagement.api.{
  AttributeApi,
  AttributeApiMarshaller,
  AttributeApiService,
  HealthApi
}
import it.pagopa.interop.attributeregistrymanagement.model.persistence.{AttributePersistentBehavior, Command}
import it.pagopa.interop.attributeregistrymanagement.model.{Attribute, AttributeKind, AttributeSeed, Problem}
import it.pagopa.interop.attributeregistrymanagement.server.Controller
import it.pagopa.interop.attributeregistrymanagement.server.impl.Main.behaviorFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import org.scalactic.Equality

class AttributeApiServiceSpec
    extends ScalaTestWithActorTestKit(AkkaTestConfiguration.config)
    with AsyncWordSpecLike
    with Matchers {

  val partyApiMarshaller: AttributeApiMarshaller = new AttributeApiMarshallerImpl

  var controller: Option[Controller]                 = None
  var bindServer: Option[Future[Http.ServerBinding]] = None
  val sharding: ClusterSharding                      = ClusterSharding(system)

  val httpSystem: ActorSystem[Any]              =
    ActorSystem(Behaviors.ignore[Any], name = system.name, config = system.settings.config)
  implicit val classicSystem: actor.ActorSystem = httpSystem.classicSystem

  override def beforeAll(): Unit = {

    Cluster(system).manager ! Join(Cluster(system).selfMember.address)

    val persistentEntity: Entity[Command, ShardingEnvelope[Command]] =
      Entity(AttributePersistentBehavior.TypeKey)(behaviorFactory)

    sharding.init(persistentEntity)

    val wrappingDirective = SecurityDirectives.authenticateOAuth2("SecurityRealm", AdminMockAuthenticator)

    val partyApiService: AttributeApiService =
      new AttributeApiServiceImpl(
        uuidSupplier,
        timeSupplier,
        system,
        sharding,
        persistentEntity,
        mockPartyRegistryService
      )(httpSystem.executionContext)

    val attributeApi: AttributeApi =
      new AttributeApi(partyApiService, partyApiMarshaller, wrappingDirective)

    val healthApi: HealthApi = mock[HealthApi]

    controller = Some(new Controller(attributeApi, healthApi)(classicSystem))

    controller.foreach { controller =>
      bindServer = Some(
        Http()
          .newServerAt("0.0.0.0", 8088)
          .bind(controller.routes)
      )

      Await.result(bindServer.get, 100.seconds)
    }

  }

  override def afterAll(): Unit = {
    ActorTestKit.shutdown(httpSystem, 5.seconds)
    super.afterAll()
  }

  implicit val equality: Equality[Attribute] = (a: Attribute, b: Any) =>
    b match {
      case b2: Attribute =>
        a.code == b2.code &&
        a.kind == b2.kind &&
        a.description == b2.description &&
        a.origin == b2.origin &&
        a.name == b2.name
      case _             => false
    }

  "Attribute API" should {

    "create an attribute" in {
      val expected: Attribute = Attribute(
        id = UUID.randomUUID().toString,
        code = Some("123"),
        kind = AttributeKind.CERTIFIED,
        description = "this is a test",
        origin = Some("IPA"),
        name = "test",
        creationTime = OffsetDateTime.now()
      )

      val requestPayload = AttributeSeed(
        code = Some("123"),
        kind = AttributeKind.CERTIFIED,
        description = "this is a test",
        origin = Some("IPA"),
        name = "test"
      )

      createAttribute[Attribute](requestPayload).map { case (status, body) =>
        status shouldEqual StatusCodes.Created
        body shouldEqual expected
      }
    }

    "delete an attribute" in {
      val requestPayload = AttributeSeed(
        code = Some("123"),
        kind = AttributeKind.CERTIFIED,
        description = "this is a test",
        origin = Some("IPA"),
        name = "deletable"
      )

      for {
        (_, attribute) <- createAttribute[Attribute](requestPayload)
        status         <- deleteAttribute(attribute.id)
      } yield status shouldEqual StatusCodes.NoContent
    }

    "reject attribute creation when an attribute with the same name already exists" in {
      val requestPayload: AttributeSeed = AttributeSeed(
        code = Some("123"),
        kind = AttributeKind.CERTIFIED,
        description = "this is a test",
        origin = Some("IPA"),
        name = "pippo"
      )

      val requestPayloadNew: AttributeSeed = AttributeSeed(
        code = Some("444"),
        kind = AttributeKind.CERTIFIED,
        description = "Test duplicate name",
        origin = None,
        name = "pippo"
      )

      for {
        _                 <- createAttribute[Attribute](requestPayload)
        (status, problem) <- createAttribute[Problem](requestPayloadNew)
      } yield {
        status shouldEqual StatusCodes.Conflict
        problem.detail.get shouldEqual "An attribute with name = 'pippo' already exists on the registry"
      }
    }
  }

  "find an attribute by name" in {
    val expected = Attribute(
      id = UUID.randomUUID().toString,
      code = Some("999"),
      kind = AttributeKind.CERTIFIED,
      description = "Bar Foo",
      origin = Some("IPA"),
      name = "BarFoo",
      creationTime = OffsetDateTime.now()
    )

    val requestPayload: AttributeSeed = AttributeSeed(
      code = Some("999"),
      kind = AttributeKind.CERTIFIED,
      description = "Bar Foo",
      origin = Some("IPA"),
      name = "BarFoo"
    )

    for {
      _                   <- createAttribute[Attribute](requestPayload)
      (status, attribute) <- findAttributeByName("BarFoo")
    } yield {
      status shouldEqual StatusCodes.OK
      attribute shouldEqual expected
    }
  }

  "find an attribute by origin and code" in {
    val expected = Attribute(
      id = UUID.randomUUID().toString,
      code = Some("1984"),
      kind = AttributeKind.CERTIFIED,
      description = "Foo bar",
      origin = Some("IPA"),
      name = "FooBar",
      creationTime = OffsetDateTime.now()
    )

    val requestPayload = AttributeSeed(
      code = Some("1984"),
      kind = AttributeKind.CERTIFIED,
      description = "Foo bar",
      origin = Some("IPA"),
      name = "FooBar"
    )

    for {
      _                   <- createAttribute[Attribute](requestPayload)
      (status, attribute) <- findAttributeByOriginAndCode("IPA", "1984")
    } yield {
      status shouldEqual StatusCodes.OK
      attribute shouldEqual expected
    }
  }

  "load attributes from proxy" in {
    val expected = Attribute(
      id = UUID.randomUUID().toString,
      code = Some("YADA"),
      kind = AttributeKind.CERTIFIED,
      description = "Proxied",
      origin = Some("IPA"),
      name = "Proxied",
      creationTime = OffsetDateTime.now()
    )

    for {
      _                   <- loadAttributes
      (status, attribute) <- findAttributeByName("Proxied")
    } yield {
      status shouldEqual StatusCodes.OK
      attribute shouldEqual expected
    }
  }
}

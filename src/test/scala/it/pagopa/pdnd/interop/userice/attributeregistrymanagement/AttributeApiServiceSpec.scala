package it.pagopa.pdnd.interop.userice.attributeregistrymanagement

import akka.actor
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.{Cluster, Join}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.api.impl.{
  AttributeApiMarshallerImpl,
  AttributeApiServiceImpl
}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.api.{
  AttributeApi,
  AttributeApiMarshaller,
  AttributeApiService,
  HealthApi
}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.common.system.Authenticator
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.{Attribute, AttributeSeed, Problem}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.server.Controller
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.server.impl.Main
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

class AttributeApiServiceSpec
    extends ScalaTestWithActorTestKit(AkkaTestConfiguration.config)
    with AnyWordSpecLike
    with Matchers {

  val partyApiMarshaller: AttributeApiMarshaller = new AttributeApiMarshallerImpl

  var controller: Option[Controller]                 = None
  var bindServer: Option[Future[Http.ServerBinding]] = None
  val sharding: ClusterSharding                      = ClusterSharding(system)

  val httpSystem: ActorSystem[Any] =
    ActorSystem(Behaviors.ignore[Any], name = system.name, config = system.settings.config)
  implicit val executionContext: ExecutionContextExecutor = httpSystem.executionContext
  implicit val classicSystem: actor.ActorSystem           = httpSystem.classicSystem

  override def beforeAll(): Unit = {

    val persistentEntity = Main.buildPersistentEntity()

    Cluster(system).manager ! Join(Cluster(system).selfMember.address)

    sharding.init(persistentEntity)

    val wrappingDirective = SecurityDirectives.authenticateOAuth2("SecurityRealm", Authenticator)

    val partyApiService: AttributeApiService =
      new AttributeApiServiceImpl(uuidSupplier, system, sharding, persistentEntity)

    val attributeApi: AttributeApi =
      new AttributeApi(partyApiService, partyApiMarshaller, wrappingDirective)

    val healthApi: HealthApi = mock[HealthApi]

    controller = Some(new Controller(attributeApi, healthApi)(classicSystem))

    controller foreach { controller =>
      bindServer = Some(
        Http()
          .newServerAt("0.0.0.0", 8088)
          .bind(controller.routes)
      )

      Await.result(bindServer.get, 100.seconds)
    }

  }

  override def afterAll(): Unit = {
    bindServer.foreach(_.foreach(_.unbind()))
    ActorTestKit.shutdown(httpSystem, 5.seconds)
    super.afterAll()
  }

  "Attribute API" should {

    "create an attribute" in {
      //given
      val mockUUID = "a7aa1933-b966-0fc2-05a4-e1e0e46615e9"
      (() => uuidSupplier.get).expects().returning(UUID.fromString(mockUUID)).once()

      //when
      val requestPayload = AttributeSeed(
        code = Some("123"),
        certified = true,
        description = "this is a test",
        origin = Some("IPA"),
        name = "test"
      )
      val response = createAttribute(buildPayload(requestPayload))
      val body     = Await.result(Unmarshal(response.entity).to[Attribute], Duration.Inf)

      //then
      response.status shouldBe StatusCodes.Created
      body.id shouldBe mockUUID
      body.name shouldBe "test"
      body.certified shouldBe true
      body.code.get shouldBe "123"
      body.description shouldBe "this is a test"
      body.origin.get shouldBe "IPA"
    }

    "reject attribute creation when an attribute with the same name already exists" in {
      //given an attribute registry
      val mockUUID = "a7aa1933-b966-0fc2-05a4-e1e0e4661511"
      (() => uuidSupplier.get).expects().returning(UUID.fromString(mockUUID)).once()

      val requestPayload = AttributeSeed(
        code = Some("123"),
        certified = true,
        description = "this is a test",
        origin = Some("IPA"),
        name = "pippo"
      )
      createAttribute(buildPayload(requestPayload))

      //when
      val mockUUIDNew = "a7aa1933-b966-0fc2-05a4-e1e0e4661510"
      (() => uuidSupplier.get).expects().returning(UUID.fromString(mockUUIDNew)).once()
      val requestPayloadNew = AttributeSeed(
        code = Some("444"),
        certified = true,
        description = "Test duplicate name",
        origin = None,
        name = "pippo"
      )
      val response = createAttribute(buildPayload(requestPayloadNew))
      val problem  = Await.result(Unmarshal(response.entity).to[Problem], Duration.Inf)

      //then
      response.status shouldBe StatusCodes.BadRequest
      problem.detail.get shouldBe "An attribute with name = 'pippo' already exists on the registry"
    }
  }

}

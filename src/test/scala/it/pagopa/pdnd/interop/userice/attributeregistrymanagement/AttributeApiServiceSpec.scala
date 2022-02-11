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
import it.pagopa.pdnd.interop.commons.utils.AkkaUtils.Authenticator
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
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.{Attribute, AttributeSeed, Problem}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.server.Controller
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.server.impl.Main
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.AttributeKind

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
      new AttributeApiServiceImpl(
        uuidSupplier,
        timeSupplier,
        system,
        sharding,
        persistentEntity,
        mockPartyRegistryService
      )

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
      val mockUUID = UUID.randomUUID()
      val time     = OffsetDateTime.now()
      (() => uuidSupplier.get).expects().returning(mockUUID).once()
      (() => timeSupplier.get).expects().returning(time).once()

      val expected = Attribute(
        id = mockUUID.toString,
        code = Some("123"),
        kind = AttributeKind.CERTIFIED,
        description = "this is a test",
        origin = Some("IPA"),
        name = "test",
        creationTime = time
      )

      //when
      val requestPayload = AttributeSeed(
        code = Some("123"),
        kind = AttributeKind.CERTIFIED,
        description = "this is a test",
        origin = Some("IPA"),
        name = "test"
      )
      val response = createAttribute(buildPayload(requestPayload))
      val body     = Unmarshal(response.entity).to[Attribute].futureValue

      //then
      response.status shouldBe StatusCodes.Created
      body shouldBe expected
    }

    "reject attribute creation when an attribute with the same name already exists" in {
      //given an attribute registry
      val mockUUID = "a7aa1933-b966-0fc2-05a4-e1e0e4661511"
      (() => uuidSupplier.get).expects().returning(UUID.fromString(mockUUID)).once()
      (() => timeSupplier.get).expects().returning(OffsetDateTime.now()).once()

      val requestPayload = AttributeSeed(
        code = Some("123"),
        kind = AttributeKind.CERTIFIED,
        description = "this is a test",
        origin = Some("IPA"),
        name = "pippo"
      )
      createAttribute(buildPayload(requestPayload))

      //when
      val requestPayloadNew = AttributeSeed(
        code = Some("444"),
        kind = AttributeKind.CERTIFIED,
        description = "Test duplicate name",
        origin = None,
        name = "pippo"
      )
      val response = createAttribute(buildPayload(requestPayloadNew))
      val problem  = Unmarshal(response.entity).to[Problem].futureValue

      //then
      response.status shouldBe StatusCodes.BadRequest
      problem.detail.get shouldBe "An attribute with name = 'pippo' already exists on the registry"
    }
  }

  "find an attribute by name" in {
    //given
    val mockUUID = UUID.randomUUID()
    val time     = OffsetDateTime.now()
    (() => uuidSupplier.get).expects().returning(mockUUID).once()
    (() => timeSupplier.get).expects().returning(time).once()

    val expected = Attribute(
      id = mockUUID.toString,
      code = Some("999"),
      kind = AttributeKind.CERTIFIED,
      description = "Bar Foo",
      origin = Some("IPA"),
      name = "BarFoo",
      creationTime = time
    )

    //when
    val requestPayload = AttributeSeed(
      code = Some("999"),
      kind = AttributeKind.CERTIFIED,
      description = "Bar Foo",
      origin = Some("IPA"),
      name = "BarFoo"
    )
    createAttribute(buildPayload(requestPayload))

    //then
    val response = findAttributeByName("BarFoo")
    val body     = Unmarshal(response.entity).to[Attribute].futureValue
    response.status shouldBe StatusCodes.OK
    body shouldBe expected
  }

  "find an attribute by origin and code" in {
    //given
    val mockUUID = UUID.randomUUID()
    val time     = OffsetDateTime.now()
    (() => uuidSupplier.get).expects().returning(mockUUID).once()
    (() => timeSupplier.get).expects().returning(time).once()

    val expected = Attribute(
      id = mockUUID.toString,
      code = Some("1984"),
      kind = AttributeKind.CERTIFIED,
      description = "Foo bar",
      origin = Some("IPA"),
      name = "FooBar",
      creationTime = time
    )

    //when
    val requestPayload = AttributeSeed(
      code = Some("1984"),
      kind = AttributeKind.CERTIFIED,
      description = "Foo bar",
      origin = Some("IPA"),
      name = "FooBar"
    )

    createAttribute(buildPayload(requestPayload))

    //then
    val response = findAttributeByOriginAndCode("IPA", "1984")
    val body     = Unmarshal(response.entity).to[Attribute].futureValue
    response.status shouldBe StatusCodes.OK
    body shouldBe expected
  }

  "load attributes from proxy" in {
    //given
    val mockUUID = UUID.randomUUID()
    val time     = OffsetDateTime.now()
    (() => uuidSupplier.get).expects().returning(mockUUID).once()
    (() => timeSupplier.get).expects().returning(time).once()

    val expected = Attribute(
      id = mockUUID.toString,
      code = Some("YADA"),
      kind = AttributeKind.CERTIFIED,
      description = "Proxied",
      origin = Some("IPA"),
      name = "Proxied",
      creationTime = time
    )

    loadAttributes

    //then
    val response = findAttributeByName("Proxied")
    val body     = Unmarshal(response.entity).to[Attribute].futureValue
    response.status shouldBe StatusCodes.OK
    body shouldBe expected
  }
}

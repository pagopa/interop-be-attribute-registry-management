package it.pagopa.interop.attributeregistrymanagement.authz

import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.Entity
import it.pagopa.interop.attributeregistrymanagement.util.AuthorizedRoutes
import it.pagopa.interop.attributeregistrymanagement.api.impl.AttributeApiMarshallerImpl._
import it.pagopa.interop.attributeregistrymanagement.api.impl.AttributeApiServiceImpl
import it.pagopa.interop.attributeregistrymanagement.model.{AttributeKind, AttributeSeed}
import it.pagopa.interop.attributeregistrymanagement.model.persistence.Command
import it.pagopa.interop.attributeregistrymanagement.server.impl.Main.attributePersistentEntity
import it.pagopa.interop.attributeregistrymanagement.service.PartyRegistryService
import it.pagopa.interop.attributeregistrymanagement.util.ClusteredMUnitRouteTest
import it.pagopa.interop.partyregistryproxy.client.model.Categories
import munit.FunSuite

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.Future

class AttributeApiServiceAuthzSpec extends FunSuite with ClusteredMUnitRouteTest {

  override val testPersistentEntity: Entity[Command, ShardingEnvelope[Command]] = attributePersistentEntity

  val service = new AttributeApiServiceImpl(
    () => UUID.randomUUID(),
    () => OffsetDateTime.now(),
    testTypedSystem,
    testAkkaSharding,
    testPersistentEntity,
    new PartyRegistryService {
      override def getCategories(bearerToken: String)(implicit contexts: Seq[(String, String)]): Future[Categories] =
        Future.successful(Categories(Seq.empty, 0))
    }
  )

  test("method authorization must succeed for createAttribute") {

    val endpoint = AuthorizedRoutes.endpoints("createAttribute")
    val fakeSeed =
      AttributeSeed(code = None, kind = AttributeKind.CERTIFIED, description = "???", origin = None, name = "???")
    validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.createAttribute(fakeSeed) })
  }

  test("method authorization must succeed for getAttributeById") {
    val endpoint = AuthorizedRoutes.endpoints("getAttributeById")
    validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.getAttributeById("fakeSeed") })
  }

  test("method authorization must succeed for getAttributeByName") {
    val endpoint = AuthorizedRoutes.endpoints("getAttributeByName")
    validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.getAttributeByName("fakeSeed") })
  }

  test("method authorization must succeed for getAttributes") {
    val endpoint = AuthorizedRoutes.endpoints("getAttributes")
    validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.getAttributes(None) })
  }

  test("method authorization must succeed for getBulkedAttributes") {
    val endpoint = AuthorizedRoutes.endpoints("getBulkedAttributes")
    validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.getBulkedAttributes(None) })
  }

  test("method authorization must succeed for getAttributeByOriginAndCode") {
    val endpoint = AuthorizedRoutes.endpoints("getAttributeByOriginAndCode")
    validateAuthorization(
      endpoint,
      { implicit c: Seq[(String, String)] => service.getAttributeByOriginAndCode("fakeSeed", "code") }
    )
  }

  test("method authorization must succeed for loadCertifiedAttributes") {
    val endpoint = AuthorizedRoutes.endpoints("loadCertifiedAttributes")
    validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.loadCertifiedAttributes() })
  }

  test("method authorization must succeed for deleteAttributeById") {
    val endpoint = AuthorizedRoutes.endpoints("deleteAttributeById")
    validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.deleteAttributeById("fakeSeed") })
  }
}

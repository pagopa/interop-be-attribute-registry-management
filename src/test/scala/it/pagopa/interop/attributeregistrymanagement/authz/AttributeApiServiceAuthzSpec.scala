package it.pagopa.interop.attributeregistrymanagement.authz

import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.Entity
import it.pagopa.interop.attributeregistrymanagement.api.impl.AttributeApiMarshallerImpl._
import it.pagopa.interop.attributeregistrymanagement.api.impl.AttributeApiServiceImpl
import it.pagopa.interop.attributeregistrymanagement.model.persistence.Command
import it.pagopa.interop.attributeregistrymanagement.server.impl.Main.attributePersistentEntity
import it.pagopa.interop.attributeregistrymanagement.util.{AuthorizedRoutes, ClusteredMUnitRouteTest}
import munit.FunSuite

import java.time.OffsetDateTime
import java.util.UUID

class AttributeApiServiceAuthzSpec extends FunSuite with ClusteredMUnitRouteTest {

  override val testPersistentEntity: Entity[Command, ShardingEnvelope[Command]] = attributePersistentEntity

  val service = new AttributeApiServiceImpl(
    () => UUID.randomUUID(),
    () => OffsetDateTime.now(),
    testTypedSystem,
    testAkkaSharding,
    testPersistentEntity
  )

  test("method authorization must succeed for deleteAttributeById") {
    val endpoint = AuthorizedRoutes.endpoints("deleteAttributeById")
    validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.deleteAttributeById("fakeSeed") })
  }
}

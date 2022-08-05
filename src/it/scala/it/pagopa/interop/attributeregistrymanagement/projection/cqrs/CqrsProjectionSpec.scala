package it.pagopa.interop.attributeregistrymanagement.projection.cqrs

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import it.pagopa.interop.attributeregistrymanagement.model.persistence.JsonFormats._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute
import it.pagopa.interop.attributeregistrymanagement.utils.PersistentAdapters._
import it.pagopa.interop.attributeregistrymanagement.{ItSpecConfiguration, ItSpecHelper}

import java.util.UUID

class CqrsProjectionSpec extends ScalaTestWithActorTestKit(ItSpecConfiguration.config) with ItSpecHelper {

  "Projection" should {
    "succeed for event AttributeAdded" in {
      val attributeId = UUID.randomUUID()

      val attribute = createAttribute(attributeId)

      val expectedData = attribute.toPersistent

      val persisted = findOne[PersistentAttribute](attributeId.toString).futureValue

      expectedData shouldBe persisted
    }

    "succeed for event AttributeDeleted" in {
      val attributeId = UUID.randomUUID()

      createAttribute(attributeId)
      deleteAttribute(attributeId)

      val persisted = find[PersistentAttribute](attributeId.toString).futureValue

      persisted shouldBe empty
    }

  }

}

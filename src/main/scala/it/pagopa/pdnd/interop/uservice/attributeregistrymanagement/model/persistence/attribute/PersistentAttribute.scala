package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.attribute

import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.{Attribute, AttributeSeed}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.service.impl.UUIDSupplier

import java.time.OffsetDateTime
import java.util.UUID

trait Persistent

final case class PersistentAttribute(
  id: UUID,
  code: Option[String],
  origin: Option[String],
  certified: Boolean,
  description: String,
  name: String,
  creationTime: OffsetDateTime
) extends Persistent

object PersistentAttribute {
  def toAPI(persistentAttribute: PersistentAttribute): Attribute = {
    Attribute(
      id = persistentAttribute.id.toString,
      code = persistentAttribute.code,
      description = persistentAttribute.description,
      certified = persistentAttribute.certified,
      origin = persistentAttribute.origin,
      name = persistentAttribute.name,
      creationTime = persistentAttribute.creationTime
    )
  }

  def fromSeed(seed: AttributeSeed, uuidSupplier: UUIDSupplier): PersistentAttribute = {
    PersistentAttribute(
      id = uuidSupplier.get,
      code = seed.code,
      certified = seed.certified,
      description = seed.description,
      origin = seed.origin,
      name = seed.name,
      creationTime = OffsetDateTime.now()
    )
  }

}

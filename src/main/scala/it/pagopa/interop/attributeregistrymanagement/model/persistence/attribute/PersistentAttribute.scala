package it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute

import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.attributeregistrymanagement.model.{Attribute, AttributeSeed}

import java.time.OffsetDateTime
import java.util.UUID

trait Persistent

final case class PersistentAttribute(
  id: UUID,
  code: Option[String],
  origin: Option[String],
  kind: PersistentAttributeKind,
  description: String,
  name: String,
  creationTime: OffsetDateTime
) extends Persistent

object PersistentAttribute {
  def toAPI(persistentAttribute: PersistentAttribute): Attribute = Attribute(
    id = persistentAttribute.id.toString,
    code = persistentAttribute.code,
    description = persistentAttribute.description,
    kind = persistentAttribute.kind.toApi,
    origin = persistentAttribute.origin,
    name = persistentAttribute.name,
    creationTime = persistentAttribute.creationTime
  )

  def fromSeed(
    seed: AttributeSeed,
    uuidSupplier: UUIDSupplier,
    timeSupplier: OffsetDateTimeSupplier
  ): PersistentAttribute = PersistentAttribute(
    id = uuidSupplier.get,
    code = seed.code,
    kind = PersistentAttributeKind.fromApi(seed.kind),
    description = seed.description,
    origin = seed.origin,
    name = seed.name,
    creationTime = timeSupplier.get
  )

}

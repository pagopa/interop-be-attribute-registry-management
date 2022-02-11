package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.attribute

import it.pagopa.pdnd.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.{Attribute, AttributeSeed}

import java.time.OffsetDateTime
import java.util.UUID
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.AttributeKind
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.AttributeKind.CERTIFIED
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.AttributeKind.DECLARED
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.AttributeKind.VERIFIED

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

sealed trait PersistentAttributeKind {
  def toApi: AttributeKind = this match {
    case Certified => AttributeKind.CERTIFIED
    case Declared  => AttributeKind.DECLARED
    case Verified  => AttributeKind.VERIFIED
  }
}

case object Certified extends PersistentAttributeKind
case object Declared  extends PersistentAttributeKind
case object Verified  extends PersistentAttributeKind

object PersistentAttributeKind {
  def fromApi(kind: AttributeKind): PersistentAttributeKind = kind match {
    case CERTIFIED => Certified
    case DECLARED  => Declared
    case VERIFIED  => Verified
  }
}

object PersistentAttribute {
  def toAPI(persistentAttribute: PersistentAttribute): Attribute = {
    Attribute(
      id = persistentAttribute.id.toString,
      code = persistentAttribute.code,
      description = persistentAttribute.description,
      kind = persistentAttribute.kind.toApi,
      origin = persistentAttribute.origin,
      name = persistentAttribute.name,
      creationTime = persistentAttribute.creationTime
    )
  }

  def fromSeed(
    seed: AttributeSeed,
    uuidSupplier: UUIDSupplier,
    timeSupplier: OffsetDateTimeSupplier
  ): PersistentAttribute = {
    PersistentAttribute(
      id = uuidSupplier.get,
      code = seed.code,
      kind = PersistentAttributeKind.fromApi(seed.kind),
      description = seed.description,
      origin = seed.origin,
      name = seed.name,
      creationTime = timeSupplier.get
    )
  }

}

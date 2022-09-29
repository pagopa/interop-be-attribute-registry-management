package it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute

import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.attributeregistrymanagement.model.{Attribute, AttributeSeed}
import it.pagopa.interop.attributeregistrymanagement.model.AttributeKind
import it.pagopa.interop.attributeregistrymanagement.model.AttributeKind._

object AttributeAdapters {

  implicit class PersistentAttributeObjectWrapper(private val p: PersistentAttribute.type) extends AnyVal {
    def toAPI(persistentAttribute: PersistentAttribute): Attribute = Attribute(
      id = persistentAttribute.id,
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
      id = uuidSupplier.get(),
      code = seed.code,
      kind = PersistentAttributeKind.fromApi(seed.kind),
      description = seed.description,
      origin = seed.origin,
      name = seed.name,
      creationTime = timeSupplier.get()
    )
  }

  implicit class PersistentAttributeKindWrapper(private val p: PersistentAttributeKind) extends AnyVal {
    def toApi: AttributeKind = p match {
      case Certified => AttributeKind.CERTIFIED
      case Declared  => AttributeKind.DECLARED
      case Verified  => AttributeKind.VERIFIED
    }
  }

  implicit class PersistentAttributeKindObjectWrapper(private val p: PersistentAttributeKind.type) extends AnyVal {
    def fromApi(kind: AttributeKind): PersistentAttributeKind = kind match {
      case CERTIFIED => Certified
      case DECLARED  => Declared
      case VERIFIED  => Verified
    }
  }

}

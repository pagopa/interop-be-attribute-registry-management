package it.pagopa.interop.attributeregistrymanagement.utils

import it.pagopa.interop.attributeregistrymanagement.model.AttributeKind._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute._
import it.pagopa.interop.attributeregistrymanagement.model.{Attribute, AttributeKind}

object PersistentAdapters {

  implicit class AttributeWrapper(private val a: Attribute) extends AnyVal {
    def toPersistent: PersistentAttribute =
      PersistentAttribute(
        id = a.id,
        code = a.code,
        origin = a.origin,
        kind = a.kind.toPersistent,
        description = a.description,
        name = a.name,
        creationTime = a.creationTime
      )
  }

  implicit class AttributeKindWrapper(private val a: AttributeKind) extends AnyVal {
    def toPersistent: PersistentAttributeKind = a match {
      case CERTIFIED => Certified
      case DECLARED  => Declared
      case VERIFIED  => Verified
    }
  }

}

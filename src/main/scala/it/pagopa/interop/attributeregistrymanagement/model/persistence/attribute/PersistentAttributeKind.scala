package it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute

import it.pagopa.interop.attributeregistrymanagement.model.AttributeKind
import it.pagopa.interop.attributeregistrymanagement.model.AttributeKind._

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

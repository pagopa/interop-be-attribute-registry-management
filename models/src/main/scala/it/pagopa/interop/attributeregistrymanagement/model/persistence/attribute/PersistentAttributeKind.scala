package it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute

sealed trait PersistentAttributeKind
case object Certified extends PersistentAttributeKind
case object Declared  extends PersistentAttributeKind
case object Verified  extends PersistentAttributeKind

object PersistentAttributeKind

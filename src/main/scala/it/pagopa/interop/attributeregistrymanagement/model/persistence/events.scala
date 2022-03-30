package it.pagopa.interop.attributeregistrymanagement.model.persistence

import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute

sealed trait Event extends Persistable

final case class AttributeAdded(attribute: PersistentAttribute) extends Event
final case class AttributeDeleted(id: String)                   extends Event

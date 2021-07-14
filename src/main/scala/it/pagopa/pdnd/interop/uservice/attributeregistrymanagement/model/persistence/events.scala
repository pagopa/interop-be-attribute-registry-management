package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute

sealed trait Event extends Persistable

final case class AttributeAdded(attribute: PersistentAttribute) extends Event
final case class AttributeDeleted(attributeId: String)          extends Event

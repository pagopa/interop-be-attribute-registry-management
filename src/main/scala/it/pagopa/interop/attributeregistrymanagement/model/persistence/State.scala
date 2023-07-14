package it.pagopa.interop.attributeregistrymanagement.model.persistence

import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute

final case class State(attributes: Map[String, PersistentAttribute]) extends Persistable {
  def add(attribute: PersistentAttribute): State = copy(attributes = attributes + (attribute.id.toString -> attribute))
  def delete(id: String): State                  = copy(attributes = attributes - id)

  def getAttribute(attributeId: String): Option[PersistentAttribute] = attributes.get(attributeId)
  def getAttributes: Seq[PersistentAttribute]                        = attributes.values.toSeq

  def getAttributeByName(name: String): Option[PersistentAttribute] =
    attributes.values.find(p => name.equalsIgnoreCase(p.name))

  def getAttributeByCodeAndName(code: String, name: String): Option[PersistentAttribute] =
    attributes.values.find(p => p.code.exists(c => code.equalsIgnoreCase(c)) && name.equalsIgnoreCase(p.name))

  def getAttributeByAttributeInfo(attributeInfo: AttributeInfo): Option[PersistentAttribute] =
    attributes.values.find(attribute =>
      attribute.origin.contains(attributeInfo.origin) && attribute.code.contains(attributeInfo.code)
    )
}

object State {
  val empty: State = State(attributes = Map.empty[String, PersistentAttribute])
}

package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute

final case class State(attributes: Map[String, PersistentAttribute]) extends Persistable {
  def add(attribute: PersistentAttribute): State = copy(attributes = attributes + (attribute.id.toString -> attribute))
  def update(updatedAttribute: PersistentAttribute): State = {
    attributes
      .get(updatedAttribute.id.toString)
      .fold(this)(_ => copy(attributes = attributes + (updatedAttribute.id.toString -> updatedAttribute)))
  }
  def getAttribute(attributeId: String): Option[PersistentAttribute] = attributes.get(attributeId)
  def getCertifiedAttributes: Seq[PersistentAttribute]               = attributes.values.filter(_.certified).toSeq
  def getAttributes: Seq[PersistentAttribute]                        = attributes.values.toSeq
  def getAttributeNames: Seq[String]                                 = attributes.values.map(_.name).toSeq

  def getAttributeByName(name: String): Option[PersistentAttribute] =
    attributes.values.find(p => name.equalsIgnoreCase(p.name))

  def getAttributeByAttributeInfo(attributeInfo: AttributeInfo): Option[PersistentAttribute] =
    attributes.values.find(attribute =>
      attribute.origin.contains(attributeInfo.origin) &&
        attribute.code.contains(attributeInfo.code)
    )
}

object State {
  val empty: State = State(attributes = Map.empty[String, PersistentAttribute])
}

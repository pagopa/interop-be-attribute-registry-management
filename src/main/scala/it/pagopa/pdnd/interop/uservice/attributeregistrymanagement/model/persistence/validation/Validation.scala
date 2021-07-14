package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.validation

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute

trait Validation {

  def validateAttributeName(
    attribute: Option[PersistentAttribute]
  ): ValidatedNel[String, Option[PersistentAttribute]] = {
    attribute match {
      case None => attribute.validNel[String]
      case Some(a) =>
        s"An attribute with name = '${a.name}' already exists on the registry".invalidNel[Option[PersistentAttribute]]
    }
  }

}

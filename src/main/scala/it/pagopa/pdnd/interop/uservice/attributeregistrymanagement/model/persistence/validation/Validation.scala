package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.validation

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.AttributeSeed
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute

trait Validation {

  def validateAttributeName(attribute: Option[PersistentAttribute]): ValidatedNel[String, Option[PersistentAttribute]] =
    attribute.fold(attribute.validNel[String])(a =>
      s"An attribute with name = '${a.name}' already exists on the registry".invalidNel[Option[PersistentAttribute]]
    )

  def validateAttributes(seeds: Seq[AttributeSeed]): ValidatedNel[String, Seq[AttributeSeed]] = {
    val hasDuplicates = seeds.groupBy(_.name).exists { case (_, group) =>
      group.size > 1
    }

    if (hasDuplicates)
      s"The request payload MUST not contain attributes with the same name".invalidNel[Seq[AttributeSeed]]
    else
      seeds.validNel[String]
  }

}

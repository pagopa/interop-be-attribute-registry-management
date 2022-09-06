package it.pagopa.interop.attributeregistrymanagement.model.persistence.validation

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import it.pagopa.interop.attributeregistrymanagement.common.system.errors.ValidationException
import it.pagopa.interop.attributeregistrymanagement.model.AttributeSeed

trait Validation {

  def validateAttributes(seeds: Seq[AttributeSeed]): ValidatedNel[ValidationException, Seq[AttributeSeed]] = {
    val hasDuplicates = seeds.groupBy(_.name).exists { case (_, group) => group.size > 1 }

    if (hasDuplicates)
      ValidationException("seed", s"The request payload MUST not contain attributes with the same name")
        .invalidNel[Seq[AttributeSeed]]
    else seeds.validNel[ValidationException]
  }

}

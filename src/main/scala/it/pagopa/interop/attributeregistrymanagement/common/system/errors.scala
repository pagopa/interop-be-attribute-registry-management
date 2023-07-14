package it.pagopa.interop.attributeregistrymanagement.common.system

import it.pagopa.interop.commons.utils.errors.ComponentError

object errors {
  final case class AttributeAlreadyPresent(name: String)
      extends ComponentError("0001", s"Attribute $name already exists")

  final case class AttributeNotFound(attributeId: String)
      extends ComponentError("0002", s"Attribute $attributeId not found")

  final case class AttributeNotFoundByName(name: String) extends ComponentError("0003", s"Attribute $name not found")

  final case class AttributeNotFoundByExternalId(origin: String, attrCode: String)
      extends ComponentError("0004", s"Attribute not found for $origin/$attrCode")

  final case class ValidationException(field: String, validationError: String)
      extends ComponentError("0005", s"Validation controls on $field do not pass - $validationError")

  final case object MissingAttributeCode extends ComponentError("0006", s"Certified attribute requires code")
}

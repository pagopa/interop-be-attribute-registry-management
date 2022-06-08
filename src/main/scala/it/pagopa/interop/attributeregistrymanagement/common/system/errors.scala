package it.pagopa.interop.attributeregistrymanagement.common.system

import scala.util.control.NoStackTrace

object errors {
  final case class AttributeAlreadyPresentException(name: String) extends NoStackTrace
}

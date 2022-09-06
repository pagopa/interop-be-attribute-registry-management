package it.pagopa.interop.attributeregistrymanagement.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCode
import it.pagopa.interop.attributeregistrymanagement.model.{
  Attribute,
  AttributeSeed,
  AttributesResponse,
  Problem,
  ProblemError
}
import it.pagopa.interop.commons.utils.SprayCommonFormats._
import it.pagopa.interop.commons.utils.errors.ComponentError
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val problemErrorFormat: RootJsonFormat[ProblemError]             = jsonFormat2(ProblemError)
  implicit val problemFormat: RootJsonFormat[Problem]                       = jsonFormat5(Problem)
  implicit val attributeSeedFormat: RootJsonFormat[AttributeSeed]           = jsonFormat5(AttributeSeed)
  implicit val attributeFormat: RootJsonFormat[Attribute]                   = jsonFormat7(Attribute)
  implicit val attributesResponseFormat: RootJsonFormat[AttributesResponse] = jsonFormat1(AttributesResponse)

  final val serviceErrorCodePrefix: String = "004"
  final val defaultProblemType: String     = "about:blank"
  final val defaultErrorMessage: String    = "Unknown error"

  def problemOf(httpError: StatusCode, error: ComponentError): Problem =
    Problem(
      `type` = defaultProblemType,
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors = Seq(
        ProblemError(
          code = s"$serviceErrorCodePrefix-${error.code}",
          detail = Option(error.getMessage).getOrElse(defaultErrorMessage)
        )
      )
    )

  def problemOf(httpError: StatusCode, errors: List[ComponentError]): Problem =
    Problem(
      `type` = defaultProblemType,
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors = errors.map(error =>
        ProblemError(
          code = s"$serviceErrorCodePrefix-${error.code}",
          detail = Option(error.getMessage).getOrElse(defaultErrorMessage)
        )
      )
    )

  final val entityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

}

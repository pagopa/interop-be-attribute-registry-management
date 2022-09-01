package it.pagopa.interop.attributeregistrymanagement.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import it.pagopa.interop.commons.utils.SprayCommonFormats._
import it.pagopa.interop.attributeregistrymanagement.model.{Attribute, AttributeSeed, AttributesResponse, Problem}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val problemFormat: RootJsonFormat[Problem]                       = jsonFormat3(Problem)
  implicit val attributeSeedFormat: RootJsonFormat[AttributeSeed]           = jsonFormat5(AttributeSeed)
  implicit val attributeFormat: RootJsonFormat[Attribute]                   = jsonFormat7(Attribute)
  implicit val attributesResponseFormat: RootJsonFormat[AttributesResponse] = jsonFormat1(AttributesResponse)

}

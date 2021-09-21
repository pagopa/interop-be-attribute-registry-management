package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.{
  Attribute,
  AttributeSeed,
  AttributesResponse,
  BulkedAttributesRequest,
  Problem
}
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat, deserializationError}

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset}
import scala.util.Try

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val localTimeFormat: JsonFormat[OffsetDateTime] = new JsonFormat[OffsetDateTime] {

    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val deserializationErrorMessage =
      s"Expected date time in ISO offset date time format ex. ${OffsetDateTime.now().format(formatter)}"

    override def write(obj: OffsetDateTime): JsValue = JsString(formatter.format(obj))

    override def read(json: JsValue): OffsetDateTime = {
      json match {
        case JsString(lTString) =>
          Try(OffsetDateTime.of(LocalDateTime.parse(lTString, formatter), ZoneOffset.UTC))
            .getOrElse(deserializationError(deserializationErrorMessage))
        case _ => deserializationError(deserializationErrorMessage)
      }
    }
  }

  implicit val problemFormat: RootJsonFormat[Problem]                       = jsonFormat3(Problem)
  implicit val attributeSeedFormat: RootJsonFormat[AttributeSeed]           = jsonFormat5(AttributeSeed)
  implicit val attributeFormat: RootJsonFormat[Attribute]                   = jsonFormat7(Attribute)
  implicit val attributesResponseFormat: RootJsonFormat[AttributesResponse] = jsonFormat1(AttributesResponse)
  implicit val bulkedAttributesRequestFormat: RootJsonFormat[BulkedAttributesRequest] = jsonFormat1(
    BulkedAttributesRequest
  )
}

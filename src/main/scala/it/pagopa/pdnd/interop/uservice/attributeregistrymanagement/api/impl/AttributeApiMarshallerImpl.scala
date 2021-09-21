package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.api.AttributeApiMarshaller
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model._
import spray.json._

class AttributeApiMarshallerImpl extends AttributeApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {
  override implicit def fromEntityUnmarshallerAttributeSeed: FromEntityUnmarshaller[AttributeSeed] =
    sprayJsonUnmarshaller[AttributeSeed]

  override implicit def toEntityMarshallerAttribute: ToEntityMarshaller[Attribute] = sprayJsonMarshaller[Attribute]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  override implicit def toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse] =
    sprayJsonMarshaller[AttributesResponse]

  implicit def toEntityMarshallerAttributeSeed: ToEntityMarshaller[AttributeSeed] = sprayJsonMarshaller[AttributeSeed]
  implicit def fromEntityUnmarshallerAttribute: FromEntityUnmarshaller[Attribute] = sprayJsonUnmarshaller[Attribute]

  override implicit def fromEntityUnmarshallerAttributeSeedList: FromEntityUnmarshaller[Seq[AttributeSeed]] =
    sprayJsonUnmarshaller[Seq[AttributeSeed]]

  override implicit def fromEntityUnmarshallerBulkedAttributesRequest: FromEntityUnmarshaller[BulkedAttributesRequest] =
    sprayJsonUnmarshaller[BulkedAttributesRequest]
}

package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.serializer.v1

import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.serializer.v1.attribute.AttributeV1

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset}
import java.util.UUID
import scala.util.Try

object protobufUtils {

  private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  def toPersistentAttribute(attr: AttributeV1): Either[Throwable, PersistentAttribute] = {
    for {
      uuid <- Try {
        UUID.fromString(attr.id)
      }.toEither
    } yield PersistentAttribute(
      id = uuid,
      code = attr.code,
      certified = attr.certified,
      description = attr.description,
      origin = attr.origin,
      name = attr.name,
      creationTime = toTime(attr.creationTime)
    )
  }

  def toProtobufAttribute(attr: PersistentAttribute): AttributeV1 = AttributeV1(
    id = attr.id.toString,
    code = attr.code,
    certified = attr.certified,
    description = attr.description,
    origin = attr.origin,
    name = attr.name,
    creationTime = fromTime(attr.creationTime)
  )

  def fromTime(timestamp: OffsetDateTime): String = timestamp.format(formatter)
  def toTime(timestamp: String): OffsetDateTime = {
    OffsetDateTime.of(LocalDateTime.parse(timestamp, formatter), ZoneOffset.UTC)
  }
}

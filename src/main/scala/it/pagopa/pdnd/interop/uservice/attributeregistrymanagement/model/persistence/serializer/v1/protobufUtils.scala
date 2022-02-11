package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.serializer.v1

import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.serializer.v1.attribute.AttributeV1

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset}
import java.util.UUID
import scala.util.Try
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.serializer.v1.attribute.AttributeKindV1
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.serializer.v1.attribute.AttributeKindV1._
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.attribute._

object protobufUtils {

  private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  def toPersistentAttribute(attr: AttributeV1): Either[Throwable, PersistentAttribute] = {
    for {
      uuid <- Try {
        UUID.fromString(attr.id)
      }.toEither
      kind <- fromProtoKind(attr.kind)
    } yield PersistentAttribute(
      id = uuid,
      code = attr.code,
      kind = kind,
      description = attr.description,
      origin = attr.origin,
      name = attr.name,
      creationTime = toTime(attr.creationTime)
    )
  }

  def toProtobufAttribute(attr: PersistentAttribute): AttributeV1 = AttributeV1(
    id = attr.id.toString,
    code = attr.code,
    kind = toProtoKind(attr.kind),
    description = attr.description,
    origin = attr.origin,
    name = attr.name,
    creationTime = fromTime(attr.creationTime)
  )

  private def fromProtoKind(kind: AttributeKindV1): Either[Throwable, PersistentAttributeKind] = kind match {
    case CERTIFIED           => Right(Certified)
    case DECLARED            => Right(Declared)
    case VERIFIED            => Right(Verified)
    case Unrecognized(value) => Left(new RuntimeException(s"Unable to deserialize kind value $value"))
  }

  private def toProtoKind(kind: PersistentAttributeKind): AttributeKindV1 = kind match {
    case Certified => CERTIFIED
    case Declared  => DECLARED
    case Verified  => VERIFIED
  }

  def fromTime(timestamp: OffsetDateTime): String = timestamp.format(formatter)
  def toTime(timestamp: String): OffsetDateTime = {
    OffsetDateTime.of(LocalDateTime.parse(timestamp, formatter), ZoneOffset.UTC)
  }
}

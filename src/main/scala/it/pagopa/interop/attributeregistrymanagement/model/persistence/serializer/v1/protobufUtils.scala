package it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.v1

import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.v1.attribute.AttributeKindV1._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.v1.attribute.{
  AttributeKindV1,
  AttributeV1
}
import it.pagopa.interop.commons.utils.TypeConversions._

import scala.util.Try

object protobufUtils {

  def toPersistentAttribute(attr: AttributeV1): Either[Throwable, PersistentAttribute] = for {
    uuid         <- attr.id.toUUID.toEither
    creationTime <- attr.creationTime.toOffsetDateTime.toEither
    kind         <- fromProtoKind(attr.kind)
  } yield PersistentAttribute(
    id = uuid,
    code = attr.code,
    kind = kind,
    description = attr.description,
    origin = attr.origin,
    name = attr.name,
    creationTime = creationTime
  )

  def toProtobufAttribute(attr: PersistentAttribute): Try[AttributeV1] =
    attr.creationTime.asFormattedString.map(creationTime =>
      AttributeV1(
        id = attr.id.toString,
        code = attr.code,
        kind = toProtoKind(attr.kind),
        description = attr.description,
        origin = attr.origin,
        name = attr.name,
        creationTime = creationTime
      )
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

}

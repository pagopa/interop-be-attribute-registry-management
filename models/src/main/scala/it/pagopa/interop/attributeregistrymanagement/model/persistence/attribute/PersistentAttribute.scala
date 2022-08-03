package it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute

import java.time.OffsetDateTime
import java.util.UUID

trait Persistent

final case class PersistentAttribute(
  id: UUID,
  code: Option[String],
  origin: Option[String],
  kind: PersistentAttributeKind,
  description: String,
  name: String,
  creationTime: OffsetDateTime
) extends Persistent

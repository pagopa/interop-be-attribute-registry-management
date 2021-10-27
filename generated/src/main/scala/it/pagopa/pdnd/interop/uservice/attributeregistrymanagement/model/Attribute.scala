package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model

import java.time.OffsetDateTime

/**
 * = Attribute =
 *
 * Models the attribute registry entry as payload response
 *
 * @param id uniquely identifies the attribute on the registry for example: ''null''
 * @param code identifies the unique code of this attribute on the origin registry for example: ''null''
 * @param certified says if this attribute is certified for example: ''null''
 * @param description  for example: ''null''
 * @param origin represents the origin of this attribute (e.g.: IPA, Normattiva, etc.) for example: ''null''
 * @param name  for example: ''null''
 * @param creationTime  for example: ''null''
*/
final case class Attribute (
  id: String,
  code: Option[String],
  certified: Boolean,
  description: String,
  origin: Option[String],
  name: String,
  creationTime: OffsetDateTime
)


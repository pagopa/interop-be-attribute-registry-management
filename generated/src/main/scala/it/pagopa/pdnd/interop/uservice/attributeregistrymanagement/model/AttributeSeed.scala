package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model


/**
 * = AttributeSeed =
 *
 * Models the attribute registry entry as payload response
 *
 * @param code identifies the unique code of this attribute on the registry for example: ''null''
 * @param certified says if this attribute is certified for example: ''null''
 * @param description  for example: ''null''
 * @param origin represents the origin of this attribute (e.g.: IPA for the certified ones, etc.) for example: ''null''
 * @param name  for example: ''null''
*/
final case class AttributeSeed (
  code: Option[String],
  certified: Boolean,
  description: String,
  origin: Option[String],
  name: String
)


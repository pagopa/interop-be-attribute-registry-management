/**
 * Attributes Registry
 * Service managing the persistence of attributes in a local registry
 *
 * The version of the OpenAPI document: {{version}}
 * Contact: support@example.com
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */
package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.model

import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.invoker.ApiModel

case class AttributeSeed (
  /* identifies the unique code of this attribute on the registry */
  code: Option[String] = None,
  /* says if this attribute is certified */
  certified: Boolean,
  description: String,
  /* represents the origin of this attribute (e.g.: IPA for the certified ones, etc.) */
  origin: Option[String] = None,
  name: String
) extends ApiModel


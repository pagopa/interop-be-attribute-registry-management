package it.pagopa.interop.attributeregistrymanagement.model.persistence

import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute._
import it.pagopa.interop.commons.utils.SprayCommonFormats._
import spray.json.DefaultJsonProtocol._
import spray.json._

object JsonFormats {
  implicit val pakFormat: RootJsonFormat[PersistentAttributeKind] =
    new RootJsonFormat[PersistentAttributeKind] {
      override def read(json: JsValue): PersistentAttributeKind = json match {
        case JsString("Certified") => Certified
        case JsString("Declared")  => Declared
        case JsString("Verified")  => Verified
        case other => deserializationError(s"Unable to deserialize json as a PersistentAttributeKind: $other")
      }

      override def write(obj: PersistentAttributeKind): JsValue = obj match {
        case Certified => JsString("Certified")
        case Declared  => JsString("Declared")
        case Verified  => JsString("Verified")
      }
    }

  implicit val paFormat: RootJsonFormat[PersistentAttribute] = jsonFormat7(PersistentAttribute.apply)

}

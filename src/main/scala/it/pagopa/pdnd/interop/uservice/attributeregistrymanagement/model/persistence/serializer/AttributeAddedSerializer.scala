package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.AttributeAdded
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class AttributeAddedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 10000

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val AttributeAddedManifest: String = classOf[AttributeAdded].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: AttributeAdded =>
      serialize(event, AttributeAddedManifest, currentVersion)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case AttributeAddedManifest :: `version1` :: Nil =>
      deserialize(v1.events.AttributeAddedV1, bytes, manifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}
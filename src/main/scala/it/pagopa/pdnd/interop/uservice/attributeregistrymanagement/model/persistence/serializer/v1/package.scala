package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.serializer

import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.serializer.v1.events.AttributeAddedV1
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.serializer.v1.protobufUtils.{
  toPersistentAttribute,
  toProtobufAttribute
}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.serializer.v1.state.{
  StateEntryV1,
  StateV1
}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.{AttributeAdded, State}

package object v1 {

  type ErrorOr[A] = Either[Throwable, A]

  @SuppressWarnings(Array("org.wartremover.warts.Nothing"))
  implicit def stateV1PersistEventDeserializer: PersistEventDeserializer[StateV1, State] =
    state => {

      for {
        attributes <- state.attributes
          .traverse[ErrorOr, (String, PersistentAttribute)] { entry =>
            toPersistentAttribute(entry.value).map(persistentAttribute => (entry.key, persistentAttribute))
          }
          .map(_.toMap)
      } yield State(attributes)
    }

  @SuppressWarnings(Array("org.wartremover.warts.Nothing", "org.wartremover.warts.OptionPartial"))
  implicit def stateV1PersistEventSerializer: PersistEventSerializer[State, StateV1] =
    state => {
      val entries = state.attributes.toSeq.map { case (k, v) =>
        StateEntryV1(k, toProtobufAttribute(v))
      }
      Right[Throwable, StateV1](StateV1(entries))
    }

  implicit def attributesAddedV1PersistEventDeserializer: PersistEventDeserializer[AttributeAddedV1, AttributeAdded] =
    event => {
      (toPersistentAttribute(event.attribute)).map(AttributeAdded)
    }

  implicit def attributesAddedV1PersistEventSerializer: PersistEventSerializer[AttributeAdded, AttributeAddedV1] =
    event => {
      Right[Throwable, AttributeAddedV1](AttributeAddedV1(toProtobufAttribute(event.attribute)))
    }
}

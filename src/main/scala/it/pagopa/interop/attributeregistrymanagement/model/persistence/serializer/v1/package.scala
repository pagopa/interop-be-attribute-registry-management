package it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer

import cats.implicits.toTraverseOps
import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.v1.events.{
  AttributeAddedV1,
  AttributeDeletedV1
}
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.v1.protobufUtils.{
  toPersistentAttribute,
  toProtobufAttribute
}
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.v1.state.{StateEntryV1, StateV1}
import it.pagopa.interop.attributeregistrymanagement.model.persistence.{AttributeAdded, AttributeDeleted, State}

package object v1 {

  type ErrorOr[A] = Either[Throwable, A]

  implicit def stateV1PersistEventDeserializer: PersistEventDeserializer[StateV1, State] = state =>
    for {
      attributes <- state.attributes
        .traverse[ErrorOr, (String, PersistentAttribute)] { entry =>
          toPersistentAttribute(entry.value).map(persistentAttribute => (entry.key, persistentAttribute))
        }
        .map(_.toMap)
    } yield State(attributes)

  implicit def stateV1PersistEventSerializer: PersistEventSerializer[State, StateV1] = state => {
    state.attributes.toSeq
      .traverse { case (k, v) =>
        toProtobufAttribute(v).map(attribute => StateEntryV1(k, attribute))
      }
      .map(entries => StateV1(entries))
      .toEither
  }

  implicit def attributesAddedV1PersistEventDeserializer: PersistEventDeserializer[AttributeAddedV1, AttributeAdded] =
    event => toPersistentAttribute(event.attribute).map(AttributeAdded)

  implicit def attributesAddedV1PersistEventSerializer: PersistEventSerializer[AttributeAdded, AttributeAddedV1] =
    event => toProtobufAttribute(event.attribute).map(AttributeAddedV1.of).toEither

  implicit def attributesDeletedV1PersistEventDeserializer
    : PersistEventDeserializer[AttributeDeletedV1, AttributeDeleted] =
    event => Right[Throwable, AttributeDeleted](AttributeDeleted(event.id))

  implicit def attributesDeletedV1PersistEventSerializer: PersistEventSerializer[AttributeDeleted, AttributeDeletedV1] =
    event => Right[Throwable, AttributeDeletedV1](AttributeDeletedV1(event.id))

}

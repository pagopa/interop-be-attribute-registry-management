package it.pagopa.interop.attributeregistrymanagement

import cats.implicits._
import org.scalacheck.Prop.forAll
import org.scalacheck.Gen
// import cats.kernel.Eq
import munit.ScalaCheckSuite
import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.v1.attribute.AttributeKindV1
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.v1.attribute.AttributeV1
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import it.pagopa.interop.attributeregistrymanagement.model.persistence.State
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.v1.state.StateV1
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.v1.state.StateEntryV1
import PersistentSerializationSpec._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.{
  PersistEventSerializer,
  PersistEventDeserializer
}
import munit.Compare
import it.pagopa.interop.attributeregistrymanagement.model.persistence.AttributeAdded
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.v1.events.AttributeAddedV1
import it.pagopa.interop.attributeregistrymanagement.model.persistence.AttributeDeleted
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.v1.events.AttributeDeletedV1

class PersistentSerializationSpec extends ScalaCheckSuite {

  property("State is correctly deserialized") {
    forAll(stateGen) { case (state, stateV1) =>
      assertEquals(PersistEventDeserializer.from[StateV1, State](stateV1), Either.right[Throwable, State](state))
    }
  }

  property("State is correctly serialized") {
    forAll(stateGen) { case (state, stateV1) =>
      assertEquals(PersistEventSerializer.to[State, StateV1](state), Either.right[Throwable, StateV1](stateV1))
    }
  }

  property("AttributeAdded is correctly deserialized") {
    forAll(attributeAddedGen) { case (attributeAdded, attributeAddedV1) =>
      assertEquals(
        PersistEventDeserializer.from[AttributeAddedV1, AttributeAdded](attributeAddedV1),
        Either.right[Throwable, AttributeAdded](attributeAdded)
      )
    }
  }

  property("AttributeAdded is correctly serialized") {
    forAll(attributeAddedGen) { case (attributeAdded, attributeAddedV1) =>
      assertEquals(
        PersistEventSerializer.to[AttributeAdded, AttributeAddedV1](attributeAdded),
        Either.right[Throwable, AttributeAddedV1](attributeAddedV1)
      )
    }
  }

  property("AttributeDeleted is correctly deserialized") {
    forAll(attributeDeletedGen) { case (attributeDeleted, attributeDeletedV1) =>
      assertEquals(
        PersistEventDeserializer.from[AttributeDeletedV1, AttributeDeleted](attributeDeletedV1),
        Either.right[Throwable, AttributeDeleted](attributeDeleted)
      )
    }
  }

  property("AttributeDeleted is correctly serialized") {
    forAll(attributeDeletedGen) { case (attributeDeleted, attributeDeletedV1) =>
      assertEquals(
        PersistEventSerializer.to[AttributeDeleted, AttributeDeletedV1](attributeDeleted),
        Either.right[Throwable, AttributeDeletedV1](attributeDeletedV1)
      )
    }
  }

}

object PersistentSerializationSpec {

  private val offsetDatetimeGen: Gen[(OffsetDateTime, String)] = for {
    n <- Gen.chooseNum(0, 10000L)
    now = OffsetDateTime.now()
    time <- Gen.oneOf(now.minusSeconds(n), now.plusSeconds(n))
  } yield (time, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time))

  private val attributeKindGen: Gen[(PersistentAttributeKind, AttributeKindV1)] = Gen.oneOf(
    (Certified, AttributeKindV1.CERTIFIED),
    (Declared, AttributeKindV1.DECLARED),
    (Verified, AttributeKindV1.VERIFIED)
  )

  private val persistentAttributeGen: Gen[(PersistentAttribute, AttributeV1)] = for {
    id              <- Gen.uuid
    code            <- Gen.alphaNumStr.map(Option(_))
    origin          <- Gen.alphaNumStr.map(Option(_))
    (pkind, kindV1) <- attributeKindGen
    description     <- Gen.alphaNumStr
    name            <- Gen.alphaNumStr
    (time, timeV1)  <- offsetDatetimeGen
  } yield (
    PersistentAttribute(
      id = id,
      code = code,
      origin = origin,
      kind = pkind,
      description = description,
      name = name,
      creationTime = time
    ),
    AttributeV1(
      id = id.toString(),
      code = code,
      origin = origin,
      kind = kindV1,
      name = name,
      description = description,
      creationTime = timeV1
    )
  )

  private val stateFromAttrs: List[PersistentAttribute] => State =
    attrs => State(attrs.foldLeft(Map.empty[String, PersistentAttribute]) { case (m, a) => m + (a.id.toString -> a) })

  private val stateV1FromAttrs: List[AttributeV1] => StateV1 = attrsV1 =>
    StateV1(attrsV1.map(a => StateEntryV1(a.id, a)))

  val stateGen: Gen[(State, StateV1)] = Gen
    .listOf(persistentAttributeGen)
    .map(_.separate)
    .map { case (attrs, attrsV1) => (stateFromAttrs(attrs), stateV1FromAttrs(attrsV1)) }

  val attributeAddedGen: Gen[(AttributeAdded, AttributeAddedV1)] = persistentAttributeGen.flatMap {
    case (pAttr, attrV1) => (AttributeAdded(pAttr), AttributeAddedV1(attrV1))
  }

  val attributeDeletedGen: Gen[(AttributeDeleted, AttributeDeletedV1)] =
    Gen.alphaNumStr.map(str => (AttributeDeleted(str), AttributeDeletedV1(str)))

  implicit val compareState: Compare[State, State] = (stateA, stateB) => {
    stateA == stateB
  }

  implicit val compareStateV1: Compare[StateV1, StateV1] = (stateA, stateB) => {
    stateA.attributes.sortBy(_.key) == stateB.attributes.sortBy(_.key)
  }

  implicit def compareStateEither[A, B](implicit
    compare: Compare[A, B]
  ): Compare[Either[Throwable, A], Either[Throwable, B]] = {
    case (Right(a), Right(b)) => compare.isEqual(a, b)
    case (Left(a), Left(b))   => a == b
    case _                    => false
  }

}

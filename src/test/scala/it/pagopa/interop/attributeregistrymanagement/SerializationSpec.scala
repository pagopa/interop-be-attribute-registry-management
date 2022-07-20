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
}

object PersistentSerializationSpec {

  val offsetDatetimeGen: Gen[(OffsetDateTime, String)] = for {
    n <- Gen.chooseNum(0, 10000L)
    now = OffsetDateTime.now()
    time <- Gen.oneOf(now.minusSeconds(n), now.plusSeconds(n))
  } yield (time, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time))

  val attributeKindGen: Gen[(PersistentAttributeKind, AttributeKindV1)] = Gen.oneOf(
    (Certified, AttributeKindV1.CERTIFIED),
    (Declared, AttributeKindV1.DECLARED),
    (Verified, AttributeKindV1.VERIFIED)
  )

  val persistentAttributeGen: Gen[(PersistentAttribute, AttributeV1)] = for {
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

  val stateFromAttrs: List[PersistentAttribute] => State =
    attrs => State(attrs.foldLeft(Map.empty[String, PersistentAttribute]) { case (m, a) => m + (a.id.toString -> a) })

  val stateV1FromAttrs: List[AttributeV1] => StateV1 = attrsV1 => StateV1(attrsV1.map(a => StateEntryV1(a.id, a)))

  val stateGen: Gen[(State, StateV1)] = Gen
    .listOf(persistentAttributeGen)
    .map(_.separate)
    .map { case (attrs, attrsV1) => (stateFromAttrs(attrs), stateV1FromAttrs(attrsV1)) }

  implicit val compareState: Compare[State, State] = (stateA, stateB) => {
    stateA == stateB
  }

  implicit val compareStateV1: Compare[StateV1, StateV1] = (stateA, stateB) => {
    stateA.attributes.sortBy(_.key) == stateB.attributes.sortBy(_.key)
  }

  implicit val compareStateEither: Compare[Either[Throwable, State], Either[Throwable, State]] = {
    case (Right(stateA), Right(stateB)) => compareState.isEqual(stateA, stateB)
    case (Left(a), Left(b))             => a == b
    case _                              => false
  }

  implicit val compareStateV1Either: Compare[Either[Throwable, StateV1], Either[Throwable, StateV1]] = {
    case (Right(stateA), Right(stateB)) => compareStateV1.isEqual(stateA, stateB)
    case (Left(a), Left(b))             => a == b
    case _                              => false
  }

}

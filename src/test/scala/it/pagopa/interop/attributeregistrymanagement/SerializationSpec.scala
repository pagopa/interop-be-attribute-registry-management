package it.pagopa.interop.attributeregistrymanagement

import cats.implicits._
import org.scalacheck.Prop.forAll
import org.scalacheck.Gen
import cats.kernel.Eq
import munit.ScalaCheckSuite
import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.v1.attribute.AttributeKindV1
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.v1.attribute.AttributeV1
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class PersistentSerializationSpec extends ScalaCheckSuite {

//   property("State is correctly serialized") {
//     forAll(stateGenerator) { case (state, stateV1) =>
//       PersistEventSerializer.to[State, StateV1](state) === Right(stateV1)
//     }
//   }
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

}

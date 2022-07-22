package it.pagopa.interop.attributeregistrymanagement

import cats.implicits._
import org.scalacheck.Prop.forAll
import org.scalacheck.Gen
import munit.ScalaCheckSuite
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import PersistentSerializationSpec._
import com.softwaremill.diffx.Diff
import com.softwaremill.diffx.generic.auto._
import com.softwaremill.diffx.munit.DiffxAssertions
import scala.reflect.runtime.universe.{typeOf, TypeTag}
import it.pagopa.interop.attributeregistrymanagement.model.persistence._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.v1.state._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.v1.events._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.serializer.v1.attribute._

class PersistentSerializationSpec extends ScalaCheckSuite with DiffxAssertions {

  serdeCheck[State, StateV1](stateGen, _.sorted)
  serdeCheck[AttributeAdded, AttributeAddedV1](attributeAddedGen)
  serdeCheck[AttributeDeleted, AttributeDeletedV1](attributeDeletedGen)
  deserCheck[State, StateV1](stateGen)
  deserCheck[AttributeAdded, AttributeAddedV1](attributeAddedGen)
  deserCheck[AttributeDeleted, AttributeDeletedV1](attributeDeletedGen)

  // TODO move me in commons
  def serdeCheck[A: TypeTag, B](gen: Gen[(A, B)], adapter: B => B = identity[B](_))(implicit
    e: PersistEventSerializer[A, B],
    loc: munit.Location,
    d: => Diff[Either[Throwable, B]]
  ): Unit = property(s"${typeOf[A].typeSymbol.name.toString} is correctly serialized") {
    forAll(gen) { case (state, stateV1) =>
      implicit val diffX: Diff[Either[Throwable, B]] = d
      assertEqual(PersistEventSerializer.to[A, B](state).map(adapter), Right(stateV1).map(adapter))
    }
  }

  // TODO move me in commons
  def deserCheck[A, B: TypeTag](
    gen: Gen[(A, B)]
  )(implicit e: PersistEventDeserializer[B, A], loc: munit.Location, d: => Diff[Either[Throwable, A]]): Unit =
    property(s"${typeOf[B].typeSymbol.name.toString} is correctly deserialized") {
      forAll(gen) { case (state, stateV1) =>
        // * This is declared lazy in the signature to avoid a MethodTooBigException
        implicit val diffX: Diff[Either[Throwable, A]] = d
        assertEqual(PersistEventDeserializer.from[B, A](stateV1), Right(state))
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

  implicit class PimpedStateV1(val stateV1: StateV1) extends AnyVal {
    def sorted: StateV1 = stateV1.copy(stateV1.attributes.sortBy(_.key))
  }

}

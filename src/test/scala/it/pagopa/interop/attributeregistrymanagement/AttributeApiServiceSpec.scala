package it.pagopa.interop.attributeregistrymanagement

import akka.http.scaladsl.model.StatusCodes._
import it.pagopa.interop.attributeregistrymanagement.model.{Attribute, AttributeKind, AttributeSeed, Problem}
import java.time.OffsetDateTime
import java.util.UUID

// import org.scalacheck.Gen
import akka.actor.typed.ActorSystem
import munit.Compare
import scala.concurrent.ExecutionContext

class AttributeApiServiceSpec extends AkkaTestSuite {

  implicit val equality: Compare[Attribute, Attribute] = (a: Attribute, b: Attribute) =>
    a.code == b.code &&
      a.kind == b.kind &&
      a.description == b.description &&
      a.origin == b.origin &&
      a.name == b.name

  test("Attribute API should create an attribute") {
    implicit val as: ActorSystem[Nothing] = actorSystem()
    implicit val ec: ExecutionContext     = as.executionContext

    val expected: Attribute = Attribute(
      id = UUID.randomUUID().toString,
      code = Some("123"),
      kind = AttributeKind.CERTIFIED,
      description = "this is a test",
      origin = Some("IPA"),
      name = "test",
      creationTime = OffsetDateTime.now()
    )

    val requestPayload = AttributeSeed(
      code = Some("123"),
      kind = AttributeKind.CERTIFIED,
      description = "this is a test",
      origin = Some("IPA"),
      name = "test"
    )

    createAttribute[Attribute](requestPayload).map { case (status, body) =>
      assertEquals(status, Created)
      assertEquals(body, expected)
    }
  }

  test("Attribute API should create an attribute") {
    implicit val as: ActorSystem[Nothing] = actorSystem()
    implicit val ec: ExecutionContext     = as.executionContext

    val requestPayload = AttributeSeed(
      code = Some("123"),
      kind = AttributeKind.CERTIFIED,
      description = "this is a test",
      origin = Some("IPA"),
      name = "deletable"
    )

    for {
      (_, attribute) <- createAttribute[Attribute](requestPayload)
      status         <- deleteAttribute(attribute.id)
    } yield assertEquals(status, NoContent)
  }

  test("Attribute API should reject attribute creation when an attribute with the same name already exists") {
    implicit val as: ActorSystem[Nothing] = actorSystem()
    implicit val ec: ExecutionContext     = as.executionContext

    val requestPayload: AttributeSeed = AttributeSeed(
      code = Some("123"),
      kind = AttributeKind.CERTIFIED,
      description = "this is a test",
      origin = Some("IPA"),
      name = "pippo"
    )

    val requestPayloadNew: AttributeSeed = AttributeSeed(
      code = Some("444"),
      kind = AttributeKind.CERTIFIED,
      description = "Test duplicate name",
      origin = None,
      name = "pippo"
    )

    for {
      _                 <- createAttribute[Attribute](requestPayload)
      (status, problem) <- createAttribute[Problem](requestPayloadNew)
    } yield {
      assertEquals(status, Conflict)
      assertEquals(problem.detail.get, "An attribute with name = 'pippo' already exists on the registry")
    }
  }

  test("Attribute API should find an attribute by name") {
    implicit val as: ActorSystem[Nothing] = actorSystem()
    implicit val ec: ExecutionContext     = as.executionContext

    val expected = Attribute(
      id = UUID.randomUUID().toString,
      code = Some("999"),
      kind = AttributeKind.CERTIFIED,
      description = "Bar Foo",
      origin = Some("IPA"),
      name = "BarFoo",
      creationTime = OffsetDateTime.now()
    )

    val requestPayload: AttributeSeed = AttributeSeed(
      code = Some("999"),
      kind = AttributeKind.CERTIFIED,
      description = "Bar Foo",
      origin = Some("IPA"),
      name = "BarFoo"
    )

    for {
      _                   <- createAttribute[Attribute](requestPayload)
      (status, attribute) <- findAttributeByName("BarFoo")
    } yield {
      assertEquals(status, OK)
      assertEquals(attribute, expected)
    }
  }

  test("Attribute API should find an attribute by origin and code") {
    implicit val as: ActorSystem[Nothing] = actorSystem()
    implicit val ec: ExecutionContext     = as.executionContext

    val expected = Attribute(
      id = UUID.randomUUID().toString,
      code = Some("1984"),
      kind = AttributeKind.CERTIFIED,
      description = "Foo bar",
      origin = Some("IPA"),
      name = "FooBar",
      creationTime = OffsetDateTime.now()
    )

    val requestPayload = AttributeSeed(
      code = Some("1984"),
      kind = AttributeKind.CERTIFIED,
      description = "Foo bar",
      origin = Some("IPA"),
      name = "FooBar"
    )

    for {
      _                   <- createAttribute[Attribute](requestPayload)
      (status, attribute) <- findAttributeByOriginAndCode("IPA", "1984")
    } yield {
      assertEquals(status, OK)
      assertEquals(attribute, expected)
    }
  }

  test("Attribute API should load attributes from proxy") {
    implicit val as: ActorSystem[Nothing] = actorSystem()
    implicit val ec: ExecutionContext     = as.executionContext

    val expected = Attribute(
      id = UUID.randomUUID().toString,
      code = Some("YADA"),
      kind = AttributeKind.CERTIFIED,
      description = "Proxied",
      origin = Some("IPA"),
      name = "Proxied",
      creationTime = OffsetDateTime.now()
    )

    for {
      _                   <- loadAttributes
      (status, attribute) <- findAttributeByName("Proxied")
    } yield {
      assertEquals(status, OK)
      assertEquals(attribute, expected)
    }
  }

  // "return the same attributes that were bulk uploaded" in {
  //   // Let's delete the whole database and verify it's empty

  //   val nameGenerator: Gen[String] = for {
  //     nOfLetters <- Gen.chooseNum(0, 30)
  //     letters    <- Gen.pick(nOfLetters, 'a' to 'z')
  //   } yield letters.mkString

  //   val attributesGenerator: Gen[(AttributeSeed, Attribute)] = for {
  //     uuid        <- Gen.uuid.map(_.toString())
  //     code        <- Gen.chooseNum(0, 50000).map(_.toString).map(Option(_))
  //     kind        <- Gen.oneOf(AttributeKind.CERTIFIED, AttributeKind.DECLARED, AttributeKind.VERIFIED)
  //     description <- Gen.alphaStr
  //     origin = Some("IPA")
  //     name <- nameGenerator
  //   } yield (
  //     AttributeSeed(code, kind, description, origin, name),
  //     Attribute(uuid, code, kind, description, origin, name, OffsetDateTime.now())
  //   )

  //   val attributesTestCase: Gen[(List[AttributeSeed], List[Attribute])] =
  //     Gen
  //       .containerOfN[List, (AttributeSeed, Attribute)](100, attributesGenerator)
  //       .map(list => (list.map(_._1), list.map(_._2)))

  //   forAll(attributesTestCase) { case (seeds, attributes) =>
  //     for {
  //       _                         <- createBulk(seeds)
  //       (status, savedAttributes) <- getAllAttributes
  //     } yield {
  //       status shouldEqual StatusCodes.OK
  //       savedAttributes shouldEqual attributes
  //     }
  //   }
  // }
}

package it.pagopa.interop.attributeregistrymanagement

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import cats.implicits._
import it.pagopa.interop.attributeregistrymanagement.model.{Attribute, AttributeSeed, Problem}
import munit.Compare
import org.scalacheck.Prop._

import scala.concurrent.{ExecutionContext, Future}

class AttributeApiServiceSpec extends AkkaTestSuite {

  implicit val equality: Compare[Attribute, Attribute] = (a: Attribute, b: Attribute) =>
    a.code == b.code &&
      a.kind == b.kind &&
      a.description == b.description &&
      a.origin == b.origin &&
      a.name == b.name

  implicit val listEquality: Compare[List[Attribute], List[Attribute]] = (as: List[Attribute], bs: List[Attribute]) =>
    as.sortBy(_.name).zip(bs.sortBy(_.name)).forall { case (a, b) => equality.isEqual(a, b) }

  property("Attribute API should create and delete any attribute") {
    implicit val as: ActorSystem[Nothing] = actorSystem()
    implicit val ec: ExecutionContext     = as.executionContext

    forAll(attributeAndSeed) { case (attribute: Attribute, seed: AttributeSeed) =>
      val result = for {
        (status, createdAttribute) <- createAttribute[Attribute](seed)
        delStatus                  <- deleteAttribute(createdAttribute.id)
      } yield (status, createdAttribute, delStatus)

      val (status, createdAttribute, delStatus) = await(result)
      assertEquals(status, OK)
      assertEquals(delStatus, NoContent)
      assertEquals(createdAttribute, attribute)
    }
  }

  property("Attribute API should reject attribute creation when an attribute with the same name already exists") {
    implicit val as: ActorSystem[Nothing] = actorSystem()
    implicit val ec: ExecutionContext     = as.executionContext

    forAll(attributeAndSeed) { case (attribute: Attribute, seed: AttributeSeed) =>
      val result = for {
        (_, created)      <- createAttribute[Attribute](seed)
        (status, problem) <- createAttribute[Problem](seed)
        delStatus         <- deleteAttribute(created.id)
      } yield (status, problem, delStatus)

      val (status, problem: Problem, delStatus) = await(result)
      assertEquals(status, Conflict)
      assertEquals(problem.errors.head.detail, s"Attribute ${attribute.name} already exists")
      assertEquals(delStatus, NoContent)
    }
  }

  property("Attribute API should find an attribute by name") {
    implicit val as: ActorSystem[Nothing] = actorSystem()
    implicit val ec: ExecutionContext     = as.executionContext

    forAll(attributeAndSeed) { case (attribute: Attribute, seed: AttributeSeed) =>
      val result = for {
        (_, created)             <- createAttribute[Attribute](seed)
        (status, foundAttribute) <- findAttributeByName(created.name)
        delStatus                <- deleteAttribute(created.id)
      } yield (status, foundAttribute, delStatus)

      val (status, foundAttribute, delStatus) = await(result)
      assertEquals(status, OK)
      assertEquals(foundAttribute, attribute)
      assertEquals(delStatus, NoContent)
    }
  }

  property("Attribute API should find an attribute by origin and code") {
    implicit val as: ActorSystem[Nothing] = actorSystem()
    implicit val ec: ExecutionContext     = as.executionContext

    forAll(attributeAndSeed) { case (attribute: Attribute, seed: AttributeSeed) =>
      val result = for {
        (_, created)             <- createAttribute[Attribute](seed)
        (status, foundAttribute) <- findAttributeByOriginAndCode(created.origin.get, created.code.get)
        delStatus                <- deleteAttribute(created.id)
      } yield (status, foundAttribute, delStatus)

      val (status, foundAttribute, delStatus) = await(result)
      assertEquals(status, OK)
      assertEquals(foundAttribute, attribute)
      assertEquals(delStatus, NoContent)
    }
  }

  property("Attribute API should bulk create and get any attribute") {
    implicit val as: ActorSystem[Nothing] = actorSystem()
    implicit val ec: ExecutionContext     = as.executionContext

    forAll(attributesTestCase(120)) { case (seeds: List[AttributeSeed], attributes: List[Attribute]) =>
      val result = for {
        _                           <- Future.traverse(seeds)(createAttribute[Attribute])
        (status, createdAttributes) <- getAllAttributes
        _                           <- Future.traverse(createdAttributes.map(_.id))(deleteAttribute)
      } yield (status, createdAttributes)

      val (status, createdAttributes) = await(result)
      assertEquals(status, OK)
      assertEquals(createdAttributes, attributes)
    }
  }

}

package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.api.impl

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onSuccess
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import cats.data.Validated.{Invalid, Valid}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.api.AttributeApiService
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.common.system._
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model._
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence._
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute.{
  fromSeed,
  toAPI
}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.validation.Validation
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.service.impl.UUIDSupplier
import org.slf4j.{Logger, LoggerFactory}

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

/** @param uuidSupplier
  * @param system
  * @param sharding
  * @param entity
  */
@SuppressWarnings(
  Array(
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Any",
    "org.wartremover.warts.Nothing",
    "org.wartremover.warts.Recursion"
  )
)
class AttributeApiServiceImpl(
  uuidSupplier: UUIDSupplier,
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]]
) extends AttributeApiService
    with Validation {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private val settings: ClusterShardingSettings = entity.settings match {
    case None    => ClusterShardingSettings(system)
    case Some(s) => s
  }

  @inline private def getShard(id: String): String = (math.abs(id.hashCode) % settings.numberOfShards).toString

  /** Code: 201, Message: Attributes created, DataType: AttributesResponse
    * Code: 400, Message: Bad Request
    */
  override def createAttribute(attributeSeed: AttributeSeed)(implicit
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info("Creating attributes...")

    validateAttributeName(attributeByName(attributeSeed.name)) match {

      case Valid(_) =>
        val persistentAttribute = fromSeed(attributeSeed, uuidSupplier)
        val commander: EntityRef[Command] =
          sharding.entityRefFor(AttributePersistentBehavior.TypeKey, getShard(persistentAttribute.id.toString))
        val result: Future[StatusReply[Attribute]] = commander.ask(ref => CreateAttribute(persistentAttribute, ref))
        onSuccess(result) {
          case statusReply if statusReply.isSuccess => createAttribute201(statusReply.getValue)
          case statusReply if statusReply.isError =>
            createAttribute400((Problem(Option(statusReply.getError.getMessage), status = 400, "Persistence error")))
        }

      case Invalid(e) => createAttribute400(Problem(Option(e.toList.mkString(",")), status = 400, "Validation error"))
    }

  }

  /** Code: 200, Message: Attribute data, DataType: Attribute
    * Code: 404, Message: Attribute not found, DataType: Problem
    */
  override def getAttributeById(attributeId: String)(implicit
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Retrieving attribute $attributeId...")
    val commander: EntityRef[Command] =
      sharding.entityRefFor(AttributePersistentBehavior.TypeKey, getShard(attributeId))
    val result: Future[StatusReply[Attribute]] = commander.ask(ref => GetAttribute(attributeId, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => getAttributeById200(statusReply.getValue)
      case statusReply if statusReply.isError =>
        getAttributeById404(Problem(Option(statusReply.getError.getMessage), status = 404, "Attribute not found"))
    }
  }

  /** Code: 200, Message: Attribute data, DataType: Attribute
    * Code: 404, Message: Attribute not found, DataType: Problem
    */
  override def getAttributeByName(name: String)(implicit
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Retrieving attribute named $name...")
    attributeByName(name) match {
      case Some(attribute) => getAttributeByName200(toAPI(attribute))
      case None            => getAttributeByName404(Problem(Option("Attribute not found"), status = 400, "some error"))
    }
  }

  /** Code: 200, Message: array of currently available attributes, DataType: AttributesByKindResponse
    * Code: 404, Message: Attributes not found, DataType: Problem
    */
  override def getAttributes()(implicit
    toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val commanders: Seq[EntityRef[Command]] = (0 until settings.numberOfShards).map(shard =>
      sharding.entityRefFor(AttributePersistentBehavior.TypeKey, shard.toString)
    )
    val lazyAttributes: LazyList[Attribute] =
      commanders
        .to(LazyList)
        .flatMap(ref => slices(ref, 100))

    getAttributes200(AttributesResponse(attributes = lazyAttributes))
  }

  private def slices(commander: EntityRef[Command], sliceSize: Int): LazyList[Attribute] = {
    @tailrec
    def readSlice(
      commander: EntityRef[Command],
      from: Int,
      to: Int,
      lazyList: LazyList[Attribute]
    ): LazyList[Attribute] = {
      val slice: Seq[Attribute] = Await
        .result(commander.ask(ref => GetAttributes(from, to, ref)), Duration.Inf)
      if (slice.isEmpty)
        lazyList
      else {
        readSlice(commander, to, to + sliceSize, slice.to(LazyList) #::: lazyList)
      }
    }
    readSlice(commander, 0, sliceSize, LazyList.empty)
  }

  private def attributeByName(name: String): Option[PersistentAttribute] = {
    val commanders: List[EntityRef[Command]] =
      (0 until settings.numberOfShards)
        .map(shard => sharding.entityRefFor(AttributePersistentBehavior.TypeKey, shard.toString))
        .toList

    recursiveLookup(commanders, name)
  }

  @tailrec
  private def recursiveLookup(
    commanders: List[EntityRef[Command]],
    attributeName: String
  ): Option[PersistentAttribute] = {
    commanders match {
      case Nil => None
      case elem :: tail =>
        Await.result(elem.ask(ref => GetAttributeByName(attributeName, ref)), Duration.Inf) match {
          case Some(attribute) => Some(attribute)
          case None            => recursiveLookup(tail, attributeName)
        }
    }
  }

}

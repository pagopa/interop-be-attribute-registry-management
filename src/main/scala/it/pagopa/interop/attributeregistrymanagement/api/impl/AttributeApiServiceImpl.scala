package it.pagopa.interop.attributeregistrymanagement.api.impl

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete, onSuccess}
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.attributeregistrymanagement.api.AttributeApiService
import it.pagopa.interop.attributeregistrymanagement.model._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.AttributePersistentBehavior.AttributeNotFoundException
import it.pagopa.interop.attributeregistrymanagement.model.persistence._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute
import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute.{fromSeed, toAPI}
import it.pagopa.interop.attributeregistrymanagement.model.persistence.validation.Validation
import it.pagopa.interop.attributeregistrymanagement.service.PartyRegistryService
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getFutureBearer
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}
import akka.util.Timeout
import scala.concurrent.duration._

class AttributeApiServiceImpl(
  uuidSupplier: UUIDSupplier,
  timeSupplier: OffsetDateTimeSupplier,
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  partyRegistryService: PartyRegistryService
)(implicit ec: ExecutionContext)
    extends AttributeApiService
    with Validation {

  private val logger = Logger.takingImplicit[ContextFieldsToLog](LoggerFactory.getLogger(this.getClass))

  implicit val timeout: Timeout = 300.seconds

  private val settings: ClusterShardingSettings = entity.settings match {
    case None    => ClusterShardingSettings(system)
    case Some(s) => s
  }

  @inline private def getShard(id: String): String = (math.abs(id.hashCode) % settings.numberOfShards).toString

  /** Code: 201, Message: Attribute created, DataType: Attribute
    * Code: 400, Message: Bad Request, DataType: Problem
    */
  override def createAttribute(attributeSeed: AttributeSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info("Creating attribute {}", attributeSeed.name)

    validateAttributeName(attributeByCommand(GetAttributeByName, attributeSeed.name)) match {

      case Valid(_) =>
        val persistentAttribute           = fromSeed(attributeSeed, uuidSupplier, timeSupplier)
        val commander: EntityRef[Command] =
          sharding.entityRefFor(AttributePersistentBehavior.TypeKey, getShard(persistentAttribute.id.toString))
        val result: Future[Attribute]     = commander.ask(ref => CreateAttribute(persistentAttribute, ref))
        onComplete(result) {
          case Success(attribute) => createAttribute201(attribute)
          case Failure(ex)        =>
            logger.error(s"Error while creating attribute ${attributeSeed.name}", ex)
            createAttribute400(Problem(Option(ex.getMessage), status = 400, "Persistence error"))
        }

      case Invalid(e) =>
        val errors: String = e.toList.mkString(",")
        logger.error(s"Error while creating attribute ${attributeSeed.name} - $errors")
        createAttribute400(Problem(Option(errors), status = 400, "Validation error"))
    }

  }

  /** Code: 200, Message: Attribute data, DataType: Attribute
    * Code: 404, Message: Attribute not found, DataType: Problem
    */
  override def getAttributeById(attributeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info("Retrieving attribute {}", attributeId)
    val commander: EntityRef[Command]          =
      sharding.entityRefFor(AttributePersistentBehavior.TypeKey, getShard(attributeId))
    val result: Future[StatusReply[Attribute]] = commander.ask(ref => GetAttribute(attributeId, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => getAttributeById200(statusReply.getValue)
      case statusReply                          =>
        logger.error(s"Error while retrieving attribute $attributeId", statusReply.getError)
        getAttributeById404(Problem(Option(statusReply.getError.getMessage), status = 404, "Attribute not found"))
    }
  }

  /** Code: 200, Message: Attribute data, DataType: Attribute
    * Code: 404, Message: Attribute not found, DataType: Problem
    */
  override def getAttributeByName(name: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info("Retrieving attribute named {}", name)
    attributeByCommand(GetAttributeByName, name) match {
      case Some(attribute) => getAttributeByName200(toAPI(attribute))
      case None            =>
        logger.error(s"Error while retrieving attribute named $name - Attribute not found")
        getAttributeByName404(Problem(Option("Attribute not found"), status = 404, "Attribute not found"))
    }
  }

  /** Code: 200, Message: array of currently available attributes, DataType: AttributesResponse
    * Code: 404, Message: Attributes not found, DataType: Problem
    */
  override def getAttributes(search: Option[String])(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info("Retrieving attributes by search string = {}", search)
    val commanders: Seq[EntityRef[Command]] = (0 until settings.numberOfShards).map(shard =>
      sharding.entityRefFor(AttributePersistentBehavior.TypeKey, shard.toString)
    )

    def searchFn(text: String): Boolean = search.fold(true) { parameter =>
      text.toLowerCase.contains(parameter.toLowerCase)
    }

    val lazyAttributes: LazyList[Attribute] =
      commanders
        .to(LazyList)
        .flatMap(ref => slices(ref, 100))
        .filter(attr => searchFn(attr.name))

    getAttributes200(AttributesResponse(attributes = lazyAttributes.sortBy(_.name)))
  }

  /** Code: 200, Message: array of attributes, DataType: AttributesResponse
    */
  override def getBulkedAttributes(ids: Option[String])(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse]
  ): Route = {
    logger.info("Retrieving attributes in bulk fashion by identifiers in ({})", ids)
    val result: Future[Seq[StatusReply[Attribute]]] = Future.traverse(ids.getOrElse("").split(",").toList) { id =>
      val commander: EntityRef[Command] = {
        sharding.entityRefFor(AttributePersistentBehavior.TypeKey, getShard(id))
      }
      commander.ask(ref => GetAttribute(id, ref))

    }

    onSuccess(result) { replies =>
      val response = AttributesResponse(replies.filter(_.isSuccess).map(_.getValue))
      getBulkedAttributes200(response)
    }
  }

  private def slices(commander: EntityRef[Command], sliceSize: Int): LazyList[Attribute] = {
    @tailrec
    def readSlice(
      commander: EntityRef[Command],
      from: Int,
      to: Int,
      lazyList: LazyList[Attribute]
    ): LazyList[Attribute] = {

      val slice: Seq[Attribute] = Await.result(commander.ask(ref => GetAttributes(from, to, ref)), Duration.Inf)
      if (slice.isEmpty) lazyList
      else readSlice(commander, to, to + sliceSize, slice.to(LazyList) #::: lazyList)
    }

    readSlice(commander, 0, sliceSize, LazyList.empty)
  }

  private def attributeByCommand[T](
    f: (T, ActorRef[Option[PersistentAttribute]]) => Command,
    parameter: T
  ): Option[PersistentAttribute] = {
    val commanders: List[EntityRef[Command]] =
      (0 until settings.numberOfShards)
        .map(shard => sharding.entityRefFor(AttributePersistentBehavior.TypeKey, shard.toString))
        .toList

    recursiveLookup(commanders, parameter, f)
  }

  @tailrec
  private def recursiveLookup[T](
    commanders: List[EntityRef[Command]],
    parameter: T,
    f: (T, ActorRef[Option[PersistentAttribute]]) => Command
  ): Option[PersistentAttribute] = {
    commanders match {
      case Nil          => None
      case elem :: tail =>
        Await.result(elem.ask((ref: ActorRef[Option[PersistentAttribute]]) => f(parameter, ref)), Duration.Inf) match {
          case Some(attribute) => Some(attribute)
          case None            => recursiveLookup[T](tail, parameter, f)
        }
    }
  }

  case class DeltaAttributes(attributes: Set[Attribute], seeds: Set[AttributeSeed]) {
    def addAttribute(attr: Attribute): DeltaAttributes = copy(attributes = attributes + attr)
    def addSeed(seed: AttributeSeed): DeltaAttributes  = copy(seeds = seeds + seed)
  }
  object DeltaAttributes                                                            {
    def empty: DeltaAttributes = DeltaAttributes(Set.empty, Set.empty)
  }

  def addNewAttributes(attributeSeed: Seq[AttributeSeed]): Future[Set[Attribute]] = {
    // getting all the attributes already in memory
    val commanders: Seq[EntityRef[Command]]     = (0 until settings.numberOfShards).map(shard =>
      sharding.entityRefFor(AttributePersistentBehavior.TypeKey, shard.toString)
    )
    val attributesInMemory: LazyList[Attribute] =
      commanders
        .to(LazyList)
        .flatMap(ref => slices(ref, 100))

    // calculating the delta of attributes
    val delta: DeltaAttributes = attributeSeed.foldLeft[DeltaAttributes](DeltaAttributes.empty)((delta, seed) =>
      attributesInMemory
        .find(persisted => seed.name.equalsIgnoreCase(persisted.name))
        .fold(delta.addSeed(seed))(delta.addAttribute)
    )

    // for all the not existing attributes, execute the command to persist them through event sourcing
    for {
      r <- Future.traverse(delta.seeds) { attributeSeed =>
        val persistentAttribute           = fromSeed(attributeSeed, uuidSupplier, timeSupplier)
        val commander: EntityRef[Command] =
          sharding.entityRefFor(AttributePersistentBehavior.TypeKey, getShard(persistentAttribute.id.toString))
        commander.ask(ref => CreateAttribute(persistentAttribute, ref))
      }
      attributes = delta.attributes ++ r
    } yield attributes
  }

  /** Code: 201, Message: Array of created attributes and already exising ones..., DataType: AttributesResponse
    * Code: 400, Message: Bad Request, DataType: Problem
    */
  override def createAttributes(attributeSeed: Seq[AttributeSeed])(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info("Creating attributes set...")
    validateAttributes(attributeSeed) match {

      case Valid(_) =>
        val result: Future[Set[Attribute]] = addNewAttributes(attributeSeed)
        onComplete(result) {
          case Success(attributeList) =>
            createAttributes201(AttributesResponse(attributeList.toList.sortBy(_.name)))
          case Failure(ex)            =>
            logger.error(s"Error while creating attributes set", ex)
            createAttributes400(Problem(Option(ex.getMessage), status = 400, "Attributes saving error"))
        }

      case Invalid(e) =>
        val errors = e.toList.mkString(",")
        logger.error(s"Error while creating attributes set - $errors")
        createAttributes400(Problem(Option(errors), status = 400, "Validation error"))
    }

  }

  /** Code: 200, Message: Attribute data, DataType: Attribute
    * Code: 404, Message: Attribute not found, DataType: Problem
    */
  override def getAttributeByOriginAndCode(origin: String, code: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Retrieving attribute having origin $origin and code $code")
    attributeByCommand(GetAttributeByInfo, AttributeInfo(origin, code)) match {
      case Some(attribute) => getAttributeByOriginAndCode200(toAPI(attribute))
      case None            =>
        logger.error(s"Error while retrieving attribute having origin $origin and code $code - not found")
        getAttributeByOriginAndCode404(Problem(Option("Attribute not found"), status = 404, "Attribute not found"))
    }
  }

  override def loadCertifiedAttributes()(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result = for {
      bearer     <- getFutureBearer(contexts)
      categories <- partyRegistryService.getCategories(bearer)
      attributeSeeds = categories.items.map(c =>
        AttributeSeed(
          code = Option(c.code),
          kind = AttributeKind.CERTIFIED,
          description = c.name, // passing the name since no description exists at party-registry-proxy
          origin = Option(c.origin),
          name = c.name
        )
      )
      _ <- addNewAttributes(attributeSeeds)
    } yield ()

    onComplete(result) {
      case Success(_)  =>
        loadCertifiedAttributes200
      case Failure(ex) =>
        logger.error(s"Error while loading certified attributes from proxy", ex)
        loadCertifiedAttributes400(Problem(Option(ex.getMessage), status = 400, "Attributes loading error"))
    }
  }

  /**
   * Code: 204, Message: Attribute deleted
   * Code: 404, Message: Attribute not found, DataType: Problem
   */
  override def deleteAttributeById(
    attributeId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    val commander: EntityRef[Command]     =
      sharding.entityRefFor(AttributePersistentBehavior.TypeKey, getShard(attributeId))
    val result: Future[StatusReply[Unit]] = commander.ask(ref => DeleteAttribute(attributeId, ref))
    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess => deleteAttributeById204
      case Success(statusReply)                          =>
        statusReply.getError match {
          case AttributeNotFoundException =>
            val problem = Problem(None, status = 404, "Attribute not found")
            deleteAttributeById404(problem)
          case ex                         =>
            logger.error(s"Error while deleting attribute ${attributeId}", ex)
            val problem = Problem(Option(ex.getMessage), status = 500, "Internal server error")
            complete(StatusCodes.InternalServerError, problem)
        }
      case Failure(ex)                                   =>
        logger.error(s"Error while deleting attribute ${attributeId}", ex)
        val problem = Problem(Some(ex.getMessage), status = 500, "Internal server error")
        complete(StatusCodes.InternalServerError, problem)
    }
  }
}

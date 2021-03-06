package it.pagopa.interop.attributeregistrymanagement.api.impl

import cats.implicits._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete, onSuccess}
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import cats.data.Validated.{Invalid, Valid}
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
import it.pagopa.interop.attributeregistrymanagement.common.system.errors._
import com.typesafe.scalalogging.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import akka.util.Timeout
import it.pagopa.interop.commons.jwt.{ADMIN_ROLE, API_ROLE, INTERNAL_ROLE, M2M_ROLE, authorizeInterop, hasPermissions}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.OperationForbidden

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

  private val logger = Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  implicit val timeout: Timeout = 300.seconds

  private val settings: ClusterShardingSettings = entity.settings match {
    case None    => ClusterShardingSettings(system)
    case Some(s) => s
  }

  @inline private def getShard(id: String): String = (math.abs(id.hashCode) % settings.numberOfShards).toString

  private[this] def authorize(roles: String*)(
    route: => Route
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
    authorizeInterop(
      hasPermissions(roles: _*),
      Problem(Option(OperationForbidden.getMessage), status = 403, "Operation forbidden")
    )(route)

  override def createAttribute(attributeSeed: AttributeSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
    logger.info("Creating attribute {}", attributeSeed.name)

    val result: Future[Attribute] = attributeByCommand(GetAttributeByName(attributeSeed.name, _)).flatMap {
      case None       =>
        val persistentAttribute           = fromSeed(attributeSeed, uuidSupplier, timeSupplier)
        val shard                         = getShard(persistentAttribute.id.toString)
        val commander: EntityRef[Command] = sharding.entityRefFor(AttributePersistentBehavior.TypeKey, shard)
        commander.ask(CreateAttribute(persistentAttribute, _))
      case Some(attr) => Future.failed(AttributeAlreadyPresentException(attr.name))
    }

    onComplete(result) {
      case Success(attribute)                              => createAttribute201(attribute)
      case Failure(AttributeAlreadyPresentException(name)) =>
        val error: String = s"An attribute with name = '${name}' already exists on the registry"
        logger.error(s"Error while creating attribute ${name} - $error")
        createAttribute409(Problem(Option(error), status = 400, "Validation error"))
      case Failure(e)                                      =>
        logger.error(s"Error while creating attribute ${attributeSeed.name}", e)
        createAttribute400(Problem(Option(e.getMessage), status = 400, "Persistence error"))
    }
  }

  override def getAttributeById(attributeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, M2M_ROLE) {
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

  override def getAttributeByName(name: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, M2M_ROLE) {
    logger.info("Retrieving attribute named {}", name)

    onComplete(attributeByCommand(GetAttributeByName(name, _))) {
      case Success(Some(attribute)) => getAttributeByName200(toAPI(attribute))
      case Success(None)            =>
        logger.error(s"Error while retrieving attribute named $name - Attribute not found")
        getAttributeByName404(Problem(Option("Attribute not found"), status = 404, "Attribute not found"))
      case Failure(e)               =>
        logger.error(s"Error while retrieving attribute named $name - Attribute not found", e)
        complete(StatusCodes.InternalServerError, Problem(Some(e.getMessage), status = 500, "Internal server error"))
    }
  }

  override def getAttributes(search: Option[String])(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, M2M_ROLE) {
    logger.info("Retrieving attributes by search string = {}", search)

    val commanders: Seq[EntityRef[Command]] = (0 until settings.numberOfShards)
      .map(_.toString())
      .map(sharding.entityRefFor(AttributePersistentBehavior.TypeKey, _))

    def searchFn(text: String): Boolean = search.fold(true) { parameter =>
      text.toLowerCase.contains(parameter.toLowerCase)
    }

    val attributes: Future[List[Attribute]] =
      Future
        .traverse(commanders.toList)(slices(_, 100))
        .map(_.flatten.filter(attr => searchFn(attr.name)))

    onComplete(attributes) {
      case Success(attrs) => getAttributes200(AttributesResponse(attrs.sortBy(_.name)))
      case Failure(e)     =>
        logger.error(s"Error while retrieving attributes by search string = {}", search, e)
        complete(StatusCodes.InternalServerError, Problem(Some(e.getMessage), status = 500, "Internal server error"))
    }
  }

  override def getBulkedAttributes(ids: Option[String])(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
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

  private def slices(commander: EntityRef[Command], sliceSize: Int): Future[List[Attribute]] = {
    val commandIterator: Iterator[ActorRef[Seq[Attribute]] => GetAttributes] = Iterator
      .from(0, sliceSize)
      .map(n => GetAttributes(n, n + sliceSize - 1, _))

    // It's stack safe since every submission to an Execution context resets the stack, creating a trampoline effect
    def loop(acc: List[Attribute]): Future[List[Attribute]] = commander.ask(commandIterator.next()).flatMap { slice =>
      if (slice.isEmpty) Future.successful(acc) else loop(acc ++ slice)
    }

    loop(List.empty[Attribute])
  }

  private def attributeByCommand(
    command: ActorRef[Option[PersistentAttribute]] => Command
  ): Future[Option[PersistentAttribute]] = {

    // It's stack safe since every submission to an Execution context resets the stack, creating a trampoline effect
    def loop(shards: List[EntityRef[Command]]): Future[Option[PersistentAttribute]] =
      shards.headOption.fold(Future.successful(Option.empty[PersistentAttribute]))(shard =>
        shard.ask(command).flatMap {
          case x @ Some(_) => Future.successful(x)
          case None        => loop(shards.tail)
        }
      )

    val shards: List[EntityRef[Command]] = (0 until settings.numberOfShards).toList
      .map(_.toString())
      .map(sharding.entityRefFor(AttributePersistentBehavior.TypeKey, _))

    loop(shards)
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
    val commanders: Seq[EntityRef[Command]] = (0 until settings.numberOfShards)
      .map(_.toString())
      .map(sharding.entityRefFor(AttributePersistentBehavior.TypeKey, _))

    // calculating the delta of attributes
    def delta(attrs: List[Attribute]): DeltaAttributes =
      attributeSeed.foldLeft[DeltaAttributes](DeltaAttributes.empty)((delta, seed) =>
        attrs
          .find(persisted => seed.name.equalsIgnoreCase(persisted.name))
          .fold(delta.addSeed(seed))(delta.addAttribute)
      )

    def createAttribute(seed: AttributeSeed): Future[Attribute] = {
      val persistentAttribute: PersistentAttribute = fromSeed(seed, uuidSupplier, timeSupplier)
      val commander: EntityRef[Command]            =
        sharding.entityRefFor(AttributePersistentBehavior.TypeKey, getShard(persistentAttribute.id.toString))
      commander.ask(ref => CreateAttribute(persistentAttribute, ref))
    }

    // for all the not existing attributes, execute the command to persist them through event sourcing
    for {
      attributesInMem <- Future.traverse(commanders.toList)(slices(_, 100)).map(_.flatten)
      deltaAttributes = delta(attributesInMem)
      newlyCreatedAttributes <- Future.traverse(deltaAttributes.seeds)(createAttribute)
    } yield deltaAttributes.attributes ++ newlyCreatedAttributes
  }

  /** Code: 201, Message: Array of created attributes and already exising ones..., DataType: AttributesResponse
    * Code: 400, Message: Bad Request, DataType: Problem
    */
  override def createAttributes(attributeSeed: Seq[AttributeSeed])(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE) {
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
  ): Route = authorize(ADMIN_ROLE) {
    logger.info(s"Retrieving attribute having origin $origin and code $code")
    onComplete(attributeByCommand(GetAttributeByInfo(AttributeInfo(origin, code), _))) {
      case Success(Some(attribute)) => getAttributeByOriginAndCode200(toAPI(attribute))
      case Success(None)            =>
        logger.error(s"Error while retrieving attribute having origin $origin and code $code - not found")
        getAttributeByOriginAndCode404(Problem(Option("Attribute not found"), status = 404, "Attribute not found"))
      case Failure(e)               =>
        logger.error(s"Error while retrieving attribute having origin $origin and code $code", e)
        complete(StatusCodes.InternalServerError, Problem(Some(e.getMessage), status = 500, "Internal server error"))
    }
  }

  override def loadCertifiedAttributes()(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(INTERNAL_ROLE) {
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
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
    authorize(ADMIN_ROLE, API_ROLE) {
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

package it.pagopa.interop.attributeregistrymanagement.api.impl

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.attributeregistrymanagement.api.AttributeApiService
import it.pagopa.interop.attributeregistrymanagement.api.impl.ResponseHandlers._
import it.pagopa.interop.attributeregistrymanagement.common.system.errors._
import it.pagopa.interop.attributeregistrymanagement.model._
import it.pagopa.interop.attributeregistrymanagement.model.persistence._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute.AttributeAdapters._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.validation.Validation
import it.pagopa.interop.attributeregistrymanagement.service.PartyRegistryService
import it.pagopa.interop.commons.jwt._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.{getFutureBearer, getShard}
import it.pagopa.interop.commons.utils.TypeConversions.OptionOps
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

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

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  implicit val timeout: Timeout = 300.seconds

  private val settings: ClusterShardingSettings = entity.settings.getOrElse(ClusterShardingSettings(system))

  override def createAttribute(attributeSeed: AttributeSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, M2M_ROLE, INTERNAL_ROLE) {
    val operationLabel: String = s"Creating attribute ${attributeSeed.name}"
    logger.info(operationLabel)

    val result: Future[Attribute] = attributeByCommand(GetAttributeByName(attributeSeed.name, _)).flatMap {
      case None       =>
        val persistentAttribute = PersistentAttribute.fromSeed(attributeSeed, uuidSupplier, timeSupplier)
        commander(persistentAttribute.id.toString).ask(CreateAttribute(persistentAttribute, _))
      case Some(attr) => Future.failed(AttributeAlreadyPresent(attr.name))
    }

    onComplete(result) { getAgreementResponse[Attribute](operationLabel)(createAttribute200) }
  }

  override def getAttributeById(attributeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE, INTERNAL_ROLE) {
    val operationLabel: String = s"Retrieving attribute $attributeId"
    logger.info(operationLabel)

    val result: Future[Attribute] = commander(attributeId).askWithStatus(ref => GetAttribute(attributeId, ref))

    onComplete(result) { getAttributeByIdResponse[Attribute](operationLabel)(getAttributeById200) }
  }

  override def getAttributeByName(name: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE) {
    val operationLabel: String = s"Retrieving attribute named $name"
    logger.info(operationLabel)

    val result: Future[Attribute] = for {
      maybeAttribute <- attributeByCommand(GetAttributeByName(name, _))
      attribute      <- maybeAttribute.toFuture(AttributeNotFoundByName(name))
    } yield PersistentAttribute.toAPI(attribute)

    onComplete(result) {
      getAttributeByNameResponse[Attribute](operationLabel)(getAttributeByName200)
    }
  }

  override def getAttributes(search: Option[String])(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, API_ROLE, SECURITY_ROLE, M2M_ROLE) {
    val operationLabel: String = s"Retrieving attributes by search string $search"
    logger.info(operationLabel)

    val commanders: Seq[EntityRef[Command]] = (0 until settings.numberOfShards)
      .map(_.toString)
      .map(sharding.entityRefFor(AttributePersistentBehavior.TypeKey, _))

    def searchFn(text: String): Boolean = search.fold(true) { parameter =>
      text.toLowerCase.contains(parameter.toLowerCase)
    }

    val attributes: Future[AttributesResponse] =
      Future
        .traverse(commanders.toList)(slices(_, 100))
        .map(_.flatten.filter(attr => searchFn(attr.name)).sortBy(_.name))
        .map(AttributesResponse)

    onComplete(attributes) { getAttributesResponse[AttributesResponse](operationLabel)(getAttributes200) }
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
      val persistentAttribute: PersistentAttribute = PersistentAttribute.fromSeed(seed, uuidSupplier, timeSupplier)
      commander(persistentAttribute.id.toString).ask(ref => CreateAttribute(persistentAttribute, ref))
    }

    // for all the not existing attributes, execute the command to persist them through event sourcing
    for {
      attributesInMem <- Future.traverse(commanders.toList)(slices(_, 100)).map(_.flatten)
      deltaAttributes = delta(attributesInMem)
      newlyCreatedAttributes <- Future.traverse(deltaAttributes.seeds)(createAttribute)
    } yield deltaAttributes.attributes ++ newlyCreatedAttributes
  }

  override def getAttributeByOriginAndCode(origin: String, code: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize(ADMIN_ROLE, INTERNAL_ROLE, M2M_ROLE) {
    val operationLabel: String = s"Retrieving attribute having origin $origin and code $code"
    logger.info(operationLabel)

    val result: Future[Attribute] = for {
      maybeAttribute <- attributeByCommand(GetAttributeByInfo(AttributeInfo(origin, code), _))
      attribute      <- maybeAttribute.toFuture(AttributeNotFoundByExternalId(origin, code))
    } yield PersistentAttribute.toAPI(attribute)

    onComplete(result) {
      getAttributeByOriginAndCodeResponse[Attribute](operationLabel)(getAttributeByOriginAndCode200)
    }
  }

  override def loadCertifiedAttributes()(implicit contexts: Seq[(String, String)]): Route =
    authorize(INTERNAL_ROLE) {
      val operationLabel: String = s"Loading certified attributes from Party Registry"
      logger.info(operationLabel)

      val result: Future[Unit] = for {
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

      onComplete(result) { loadCertifiedAttributesResponse[Unit](operationLabel)(_ => loadCertifiedAttributes200) }
    }

  override def deleteAttributeById(
    attributeId: String
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
    authorize(ADMIN_ROLE, API_ROLE) {
      val operationLabel: String = s"Deleting attribute $attributeId"
      logger.info(operationLabel)

      val result: Future[Unit] = commander(attributeId).askWithStatus(ref => DeleteAttribute(attributeId, ref))

      onComplete(result) { deleteAttributeByIdResponse[Unit](operationLabel)(_ => deleteAttributeById204) }
    }

  private def commander(id: String): EntityRef[Command] =
    sharding.entityRefFor(AttributePersistentBehavior.TypeKey, getShard(id, settings.numberOfShards))
}

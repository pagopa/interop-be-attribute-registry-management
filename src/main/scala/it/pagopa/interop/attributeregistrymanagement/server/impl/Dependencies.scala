package it.pagopa.interop.attributeregistrymanagement.server.impl

import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, ShardedDaemonProcess}
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.persistence.typed.PersistenceId
import akka.projection.ProjectionBehavior
import com.atlassian.oai.validator.report.ValidationReport
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.attributeregistrymanagement.api.impl._
import it.pagopa.interop.attributeregistrymanagement.api.{AttributeApi, HealthApi}
import it.pagopa.interop.attributeregistrymanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.attributeregistrymanagement.common.system.ApplicationConfiguration.{
  numberOfProjectionTags,
  projectionTag
}
import it.pagopa.interop.attributeregistrymanagement.model.Problem
import it.pagopa.interop.attributeregistrymanagement.model.persistence.projection.AttributesCqrsProjection
import it.pagopa.interop.attributeregistrymanagement.model.persistence.{AttributePersistentBehavior, Command}
import it.pagopa.interop.attributeregistrymanagement.service.impl.PartyRegistryServiceImpl
import it.pagopa.interop.attributeregistrymanagement.service.{PartyProxyInvoker, PartyRegistryService}
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.utils.AkkaUtils.PassThroughAuthenticator
import it.pagopa.interop.commons.utils.OpenapiUtils
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.service.impl.{OffsetDateTimeSupplierImpl, UUIDSupplierImpl}
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.partyregistryproxy.client.api.CategoryApi
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait Dependencies {

  val uuidSupplier: UUIDSupplier               = new UUIDSupplierImpl
  val dateTimeSupplier: OffsetDateTimeSupplier = OffsetDateTimeSupplierImpl

  val behaviorFactory: EntityContext[Command] => Behavior[Command] = { entityContext =>
    val i = math.abs(entityContext.entityId.hashCode % numberOfProjectionTags)
    AttributePersistentBehavior(
      entityContext.shard,
      PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
      projectionTag(i)
    )
  }

  val attributePersistentEntity: Entity[Command, ShardingEnvelope[Command]] =
    Entity(AttributePersistentBehavior.TypeKey)(behaviorFactory)

  def initProjections()(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): Unit = {
    val dbConfig: DatabaseConfig[JdbcProfile] =
      DatabaseConfig.forConfig("akka-persistence-jdbc.shared-databases.slick")

    val mongoDbConfig = ApplicationConfiguration.mongoDb

    val projectionId   = "attributes-cqrs-projections"
    val cqrsProjection = AttributesCqrsProjection.projection(dbConfig, mongoDbConfig, projectionId)

    ShardedDaemonProcess(actorSystem).init[ProjectionBehavior.Command](
      name = projectionId,
      numberOfInstances = numberOfProjectionTags,
      behaviorFactory = (i: Int) => ProjectionBehavior(cqrsProjection.projection(projectionTag(i))),
      stopMessage = ProjectionBehavior.Stop
    )
  }

  def getJwtValidator(): Future[JWTReader] = JWTConfiguration.jwtReader
    .loadKeyset()
    .map(keyset =>
      new DefaultJWTReader with PublicKeysHolder {
        var publicKeyset: Map[KID, SerializedKey]                                        = keyset
        override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
          getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
      }
    )
    .toFuture

  private def partyProcessService(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_]): PartyRegistryService = {
    implicit val classic = actorSystem.classicSystem
    PartyRegistryServiceImpl(PartyProxyInvoker(blockingEc), CategoryApi(ApplicationConfiguration.partyProxyUrl))
  }

  def attributeApi(sharding: ClusterSharding, jwtReader: JWTReader, blockingEc: ExecutionContextExecutor)(implicit
    actorSystem: ActorSystem[_],
    ec: ExecutionContext
  ): AttributeApi = new AttributeApi(
    new AttributeApiServiceImpl(
      uuidSupplier,
      OffsetDateTimeSupplierImpl,
      actorSystem,
      sharding,
      attributePersistentEntity,
      partyProcessService(blockingEc)
    ),
    AttributeApiMarshallerImpl,
    jwtReader.OAuth2JWTValidatorAsContexts
  )

  val healthApi: HealthApi = new HealthApi(
    new HealthServiceApiImpl(),
    new HealthApiMarshallerImpl(),
    SecurityDirectives.authenticateOAuth2("SecurityRealm", PassThroughAuthenticator),
    loggingEnabled = false
  )

  val validationExceptionToRoute: ValidationReport => Route = report => {
    val message = OpenapiUtils.errorFromRequestValidationReport(report)
    complete(400, Problem(Some(message), 400, "bad request"))(AttributeApiMarshallerImpl.toEntityMarshallerProblem)
  }

}

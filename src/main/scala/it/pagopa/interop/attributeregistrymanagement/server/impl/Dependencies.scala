package it.pagopa.interop.attributeregistrymanagement.server.impl

import it.pagopa.interop.commons.utils.TypeConversions._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.ClusterEvent
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, ShardedDaemonProcess}
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.persistence.typed.PersistenceId
import akka.projection.ProjectionBehavior
import akka.{actor => classic}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import it.pagopa.interop.attributeregistrymanagement.api.impl.{
  AttributeApiMarshallerImpl,
  AttributeApiServiceImpl,
  HealthApiMarshallerImpl,
  HealthServiceApiImpl
}
import it.pagopa.interop.attributeregistrymanagement.api.{AttributeApi, HealthApi}
import it.pagopa.interop.attributeregistrymanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.attributeregistrymanagement.common.system.ApplicationConfiguration.{
  numberOfProjectionTags,
  projectionTag,
  projectionsEnabled
}
import it.pagopa.interop.attributeregistrymanagement.api.impl._
import it.pagopa.interop.attributeregistrymanagement.model.Problem
import it.pagopa.interop.attributeregistrymanagement.model.persistence.{
  AttributePersistentBehavior,
  AttributePersistentProjection,
  Command
}
import it.pagopa.interop.attributeregistrymanagement.server.Controller
import it.pagopa.interop.attributeregistrymanagement.service.impl.PartyRegistryServiceImpl
import it.pagopa.interop.attributeregistrymanagement.service.{PartyProxyInvoker, PartyRegistryService}
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.utils.AkkaUtils.PassThroughAuthenticator
import it.pagopa.interop.commons.utils.OpenapiUtils
import it.pagopa.interop.commons.utils.service.impl.{OffsetDateTimeSupplierImpl, UUIDSupplierImpl}
import it.pagopa.interop.partyregistryproxy.client.api.CategoryApi
import kamon.Kamon
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContextExecutor
import scala.util.Try
import it.pagopa.interop.commons.utils.service.UUIDSupplier
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.atlassian.oai.validator.report.ValidationReport
import akka.http.scaladsl.server.Route
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import akka.http.scaladsl.model.StatusCodes

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

    val attributePersistentProjection =
      AttributePersistentProjection(actorSystem, attributePersistentEntity, dbConfig)

    ShardedDaemonProcess(actorSystem).init[ProjectionBehavior.Command](
      name = "attribute-projections",
      numberOfInstances = numberOfProjectionTags,
      behaviorFactory = (i: Int) => ProjectionBehavior(attributePersistentProjection.projection(projectionTag(i))),
      stopMessage = ProjectionBehavior.Stop
    )
  }

  def getJwtValidator()(implicit ec: ExecutionContext): Future[JWTReader] = JWTConfiguration.jwtReader
    .loadKeyset()
    .toFuture
    .map(keyset =>
      new DefaultJWTReader with PublicKeysHolder {
        var publicKeyset: Map[KID, SerializedKey]                                        = keyset
        override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
          getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
      }
    )

  private def partyProcessService()(implicit actorSystem: ActorSystem[_]): PartyRegistryService = {
    implicit val classic = actorSystem.classicSystem
    PartyRegistryServiceImpl(PartyProxyInvoker(), CategoryApi(ApplicationConfiguration.partyProxyUrl))
  }

  def attributeApi(sharding: ClusterSharding, jwtReader: JWTReader)(implicit
    actorSystem: ActorSystem[_],
    ec: ExecutionContext
  ): AttributeApi = new AttributeApi(
    new AttributeApiServiceImpl(
      uuidSupplier,
      OffsetDateTimeSupplierImpl,
      actorSystem,
      sharding,
      attributePersistentEntity,
      partyProcessService()
    ),
    new AttributeApiMarshallerImpl(),
    jwtReader.OAuth2JWTValidatorAsContexts
  )

  val healthApi: HealthApi = new HealthApi(
    new HealthServiceApiImpl(),
    new HealthApiMarshallerImpl(),
    SecurityDirectives.authenticateOAuth2("SecurityRealm", PassThroughAuthenticator)
  )

  val validationExceptionToRoute: ValidationReport => Route = report => {
    val message = OpenapiUtils.errorFromRequestValidationReport(report)
    complete(400, Problem(Some(message), 400, "bad request"))(
      new AttributeApiMarshallerImpl().toEntityMarshallerProblem
    )
  }

}

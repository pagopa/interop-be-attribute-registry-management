package it.pagopa.interop.attributeregistrymanagement.server.impl

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, ShardedDaemonProcess}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
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
import it.pagopa.interop.commons.jwt.{JWTConfiguration, PublicKeysHolder}
import it.pagopa.interop.commons.utils.AkkaUtils.PassThroughAuthenticator
import it.pagopa.interop.commons.utils.OpenapiUtils
import it.pagopa.interop.commons.utils.service.impl.{OffsetDateTimeSupplierImpl, UUIDSupplierImpl}
import it.pagopa.interop.partyregistryproxy.client.api.CategoryApi
import kamon.Kamon
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContextExecutor
import scala.util.Try

object Main extends App {

  val dependenciesLoaded: Try[JWTReader] = for {
    keyset <- JWTConfiguration.jwtReader.loadKeyset()
    jwtValidator = new DefaultJWTReader with PublicKeysHolder {
      var publicKeyset = keyset
      override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
        getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
    }
  } yield jwtValidator

  val jwtValidator = dependenciesLoaded.get //THIS IS THE END OF THE WORLD. Exceptions are welcomed here.

  Kamon.init()

  lazy val uuidSupplier = new UUIDSupplierImpl

  def buildPersistentEntity(): Entity[Command, ShardingEnvelope[Command]] =
    Entity(typeKey = AttributePersistentBehavior.TypeKey) { entityContext =>
      AttributePersistentBehavior(
        entityContext.shard,
        PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)
      )
    }

  locally {
    val _ = ActorSystem[Nothing](
      Behaviors.setup[Nothing] { context =>
        import akka.actor.typed.scaladsl.adapter._
        val marshallerImpl                                      = new AttributeApiMarshallerImpl()
        implicit val classicSystem: classic.ActorSystem         = context.system.toClassic
        implicit val executionContext: ExecutionContextExecutor = context.system.executionContext

        val cluster = Cluster(context.system)

        context.log.info(
          "Started [" + context.system + "], cluster.selfAddress = " + cluster.selfMember.address + ", build info = " + buildinfo.BuildInfo.toString + ")"
        )

        val sharding: ClusterSharding = ClusterSharding(context.system)

        val attributePersistentEntity: Entity[Command, ShardingEnvelope[Command]] = buildPersistentEntity()

        val _ = sharding.init(attributePersistentEntity)

        val settings: ClusterShardingSettings = attributePersistentEntity.settings match {
          case None    => ClusterShardingSettings(context.system)
          case Some(s) => s
        }

        val persistence = classicSystem.classicSystem.settings.config.getString("akka.persistence.journal.plugin")

        if (persistence == "jdbc-journal") {
          val dbConfig: DatabaseConfig[JdbcProfile] =
            DatabaseConfig.forConfig("akka-persistence-jdbc.shared-databases.slick")

          val attributePersistentProjection =
            AttributePersistentProjection(context.system, attributePersistentEntity, dbConfig)

          ShardedDaemonProcess(context.system).init[ProjectionBehavior.Command](
            name = "attribute-projections",
            numberOfInstances = settings.numberOfShards,
            behaviorFactory = (i: Int) => ProjectionBehavior(attributePersistentProjection.projections(i)),
            stopMessage = ProjectionBehavior.Stop
          )
        }

        val partyProcessService: PartyRegistryService =
          PartyRegistryServiceImpl(PartyProxyInvoker(), CategoryApi(ApplicationConfiguration.partyProxyUrl))

        val attributeApi = new AttributeApi(
          new AttributeApiServiceImpl(
            uuidSupplier,
            OffsetDateTimeSupplierImpl,
            context.system,
            sharding,
            attributePersistentEntity,
            partyProcessService
          ),
          marshallerImpl,
          jwtValidator.OAuth2JWTValidatorAsContexts
        )

        val healthApi: HealthApi = new HealthApi(
          new HealthServiceApiImpl(),
          new HealthApiMarshallerImpl(),
          SecurityDirectives.authenticateOAuth2("SecurityRealm", PassThroughAuthenticator)
        )

        val _ = AkkaManagement.get(classicSystem).start()

        val controller = new Controller(
          attributeApi,
          healthApi,
          validationExceptionToRoute = Some(report => {
            val message = OpenapiUtils.errorFromRequestValidationReport(report)
            complete(400, Problem(Some(message), 400, "bad request"))(marshallerImpl.toEntityMarshallerProblem)
          })
        )

        val _ = Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(controller.routes)

        val listener = context.spawn(
          Behaviors.receive[ClusterEvent.MemberEvent]((ctx, event) => {
            ctx.log.info("MemberEvent: {}", event)
            Behaviors.same
          }),
          "listener"
        )

        Cluster(context.system).subscriptions ! Subscribe(listener, classOf[ClusterEvent.MemberEvent])

        val _ = AkkaManagement(classicSystem).start()
        ClusterBootstrap.get(classicSystem).start()
        Behaviors.empty
      },
      "pdnd-interop-uservice-attribute-registry-management"
    )
  }
}
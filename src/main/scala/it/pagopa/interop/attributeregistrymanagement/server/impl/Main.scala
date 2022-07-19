package it.pagopa.interop.attributeregistrymanagement.server.impl

import cats.syntax.all._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import akka.cluster.ClusterEvent
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http

import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement

import it.pagopa.interop.attributeregistrymanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.commons.logging.renderBuildInfo

import it.pagopa.interop.attributeregistrymanagement.server.Controller
import kamon.Kamon

import scala.concurrent.ExecutionContextExecutor
import buildinfo.BuildInfo
import com.typesafe.scalalogging.Logger
import scala.concurrent.Future
import scala.util.{Success, Failure}
import akka.actor.typed.DispatcherSelector

object Main extends App with Dependencies {

  Kamon.init()

  val logger: Logger = Logger(this.getClass())

  System.setProperty("kanela.show-banner", "false")

  val actorSystem: ActorSystem[Nothing] = ActorSystem[Nothing](
    Behaviors.setup[Nothing] { context =>
      implicit val actorSystem: ActorSystem[Nothing]          = context.system
      implicit val executionContext: ExecutionContextExecutor = actorSystem.executionContext
      val selector: DispatcherSelector                        = DispatcherSelector.fromConfig("futures-dispatcher")
      val blockingEc: ExecutionContextExecutor                = actorSystem.dispatchers.lookup(selector)

      AkkaManagement.get(actorSystem.classicSystem).start()

      val sharding: ClusterSharding = ClusterSharding(actorSystem)
      sharding.init(attributePersistentEntity)

      val cluster: Cluster = Cluster(actorSystem)
      ClusterBootstrap.get(actorSystem.classicSystem).start()

      val listener = context.spawn(
        Behaviors.receive[ClusterEvent.MemberEvent]((ctx, event) => {
          ctx.log.debug("MemberEvent: {}", event)
          Behaviors.same
        }),
        "listener"
      )

      cluster.subscriptions ! Subscribe(listener, classOf[ClusterEvent.MemberEvent])

      if (ApplicationConfiguration.projectionsEnabled) initProjections()

      logger.info(renderBuildInfo(BuildInfo))
      logger.info(s"Started cluster at ${cluster.selfMember.address}")

      val serverBinding: Future[Http.ServerBinding] = for {
        jwtReader <- getJwtValidator()
        api        = attributeApi(sharding, jwtReader, blockingEc)
        controller = new Controller(api, healthApi, validationExceptionToRoute.some)(actorSystem.classicSystem)
        binding <- Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(controller.routes)
      } yield binding

      serverBinding.onComplete {
        case Success(b) =>
          logger.info(s"Started server at ${b.localAddress.getHostString()}:${b.localAddress.getPort()}")
        case Failure(e) =>
          actorSystem.terminate()
          logger.error("Startup error: ", e)
      }

      Behaviors.empty
    },
    BuildInfo.name
  )

  actorSystem.whenTerminated.onComplete { case _ => Kamon.stop() }(scala.concurrent.ExecutionContext.global)

}

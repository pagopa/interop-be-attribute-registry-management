package it.pagopa.interop.attributeregistrymanagement.model.persistence

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import it.pagopa.interop.attributeregistrymanagement.common.system.errors.AttributeNotFound
import it.pagopa.interop.attributeregistrymanagement.model.Attribute
import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute._
import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute.AttributeAdapters._

import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{DurationInt, DurationLong}
import scala.language.postfixOps

object AttributePersistentBehavior {

  def commandHandler(
    shard: ActorRef[ClusterSharding.ShardCommand],
    context: ActorContext[Command]
  ): (State, Command) => Effect[Event, State] = { (state, command) =>
    val idleTimeout =
      context.system.settings.config.getDuration("attribute-registry-management.idle-timeout")
    context.setReceiveTimeout(idleTimeout.get(ChronoUnit.SECONDS) seconds, Idle)
    command match {
      case CreateAttribute(attribute, replyTo) =>
        Effect
          .persist(AttributeAdded(attribute))
          .thenRun((_: State) => replyTo ! PersistentAttribute.toAPI(attribute))

      case DeleteAttribute(attributeId, replyTo) =>
        state
          .getAttribute(attributeId)
          .fold {
            replyTo ! StatusReply.Error[Unit](AttributeNotFound(attributeId))
            Effect.none[AttributeDeleted, State]
          } { _ =>
            Effect
              .persist(AttributeDeleted(attributeId))
              .thenRun((_: State) => replyTo ! StatusReply.success(()))
          }

      case GetAttribute(attributeId, replyTo) =>
        val reply = state
          .getAttribute(attributeId)
          .fold(StatusReply.Error[Attribute](AttributeNotFound(attributeId)))(a =>
            StatusReply.Success(PersistentAttribute.toAPI(a))
          )
        replyTo ! reply
        Effect.none[Event, State]

      case GetAttributes(from, to, replyTo) =>
        val reply = state.getAttributes.slice(from, to).map(PersistentAttribute.toAPI)
        replyTo ! reply
        Effect.none[Event, State]

      case GetAttributeByName(name, replyTo) =>
        replyTo ! state.getAttributeByName(name)
        Effect.none[Event, State]

      case GetAttributeByCodeAndName(code, name, replyTo) =>
        replyTo ! state.getAttributeByCodeAndName(code, name)
        Effect.none[Event, State]

      case GetAttributeByInfo(attributeInfo, replyTo) =>
        replyTo ! state.getAttributeByAttributeInfo(attributeInfo)
        Effect.none[Event, State]

      case Idle =>
        shard ! ClusterSharding.Passivate(context.self)
        context.log.debug(s"Passivate shard: ${shard.path.name}")
        Effect.none[Event, State]
    }
  }

  val eventHandler: (State, Event) => State = (state, event) =>
    event match {
      case AttributeAdded(attribute) => state.add(attribute)
      case AttributeDeleted(id)      => state.delete(id)
    }

  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("interop-be-attribute-registry-management-persistence")

  def apply(
    shard: ActorRef[ClusterSharding.ShardCommand],
    persistenceId: PersistenceId,
    projectionTag: String
  ): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.debug(
        s"Starting Attribute" +
          s" Shard ${persistenceId.id}"
      )
      val numberOfEvents = context.system.settings.config
        .getInt("attribute-registry-management.number-of-events-before-snapshot")
      EventSourcedBehavior[Command, Event, State](
        persistenceId = persistenceId,
        emptyState = State.empty,
        commandHandler = commandHandler(shard, context),
        eventHandler = eventHandler
      ).withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = numberOfEvents, keepNSnapshots = 1))
        .withTagger(_ => Set(projectionTag))
        .onPersistFailure(SupervisorStrategy.restartWithBackoff(200 millis, 5 seconds, 0.1))
    }
  }
}

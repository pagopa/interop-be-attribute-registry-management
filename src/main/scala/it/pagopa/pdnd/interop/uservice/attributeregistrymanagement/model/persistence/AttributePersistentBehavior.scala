package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.Attribute
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute.toAPI

import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{DurationInt, DurationLong}
import scala.language.postfixOps

object AttributePersistentBehavior {

  final case object AttributeNotFoundException extends Throwable

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
          .thenRun((_: State) => replyTo ! (toAPI(attribute)))

      case GetAttribute(attributeId, replyTo) =>
        val reply = state
          .getAttribute(attributeId)
          .fold(StatusReply.Error[Attribute](AttributeNotFoundException))(a => StatusReply.Success(toAPI(a)))
        replyTo ! reply
        Effect.none[Event, State]

      case GetAttributes(from, to, replyTo) =>
        val reply = state.getAttributes.slice(from, to).map(toAPI)
        replyTo ! reply
        Effect.none[Event, State]

      case GetAttributeByName(name, replyTo) =>
        replyTo ! state.getAttributeByName(name)
        Effect.none[Event, State]

      case GetAttributeByInfo(attributeInfo, replyTo) =>
        replyTo ! state.getAttributeByAttributeInfo(attributeInfo)
        Effect.none[Event, State]

      case Idle =>
        shard ! ClusterSharding.Passivate(context.self)
        context.log.info(s"Passivate shard: ${shard.path.name}")
        Effect.none[Event, State]
    }
  }

  val eventHandler: (State, Event) => State = (state, event) =>
    event match {
      case AttributeAdded(attribute) => state.add(attribute)
    }

  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("pdnd-interop-uservice-attribute-registry-management-persistence-attribute")

  def apply(shard: ActorRef[ClusterSharding.ShardCommand], persistenceId: PersistenceId): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info(
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
        .withTagger(_ => Set(persistenceId.id))
        .onPersistFailure(SupervisorStrategy.restartWithBackoff(200 millis, 5 seconds, 0.1))
    }
  }
}

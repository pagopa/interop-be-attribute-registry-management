package it.pagopa.interop.attributeregistrymanagement.model.persistence

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import it.pagopa.interop.attributeregistrymanagement.model.Attribute
import it.pagopa.interop.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute

sealed trait Command

final case class CreateAttribute(attribute: PersistentAttribute, replyTo: ActorRef[Attribute])           extends Command
final case class DeleteAttribute(attributeId: String, replyTo: ActorRef[StatusReply[Unit]])              extends Command
final case class GetAttribute(attributeId: String, replyTo: ActorRef[StatusReply[Attribute]])            extends Command
final case class GetAttributes(from: Int, to: Int, replyTo: ActorRef[Seq[Attribute]])                    extends Command
final case class GetAttributeByName(name: String, replyTo: ActorRef[Option[PersistentAttribute]])        extends Command
final case class GetAttributeByCodeAndName(code: String, name: String, replyTo: ActorRef[Option[PersistentAttribute]])
    extends Command
final case class GetAttributeByInfo(info: AttributeInfo, replyTo: ActorRef[Option[PersistentAttribute]]) extends Command
case object Idle                                                                                         extends Command

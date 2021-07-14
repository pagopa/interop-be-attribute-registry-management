package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence

import akka.Done
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.Attribute
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.persistence.attribute.PersistentAttribute

sealed trait Command

final case class CreateAttribute(attribute: PersistentAttribute, replyTo: ActorRef[StatusReply[Attribute]])
    extends Command
final case class DeleteAttribute(attributeId: String, replyTo: ActorRef[StatusReply[Done]])       extends Command
final case class GetAttribute(attributeId: String, replyTo: ActorRef[StatusReply[Attribute]])     extends Command
final case class GetAttributes(from: Int, to: Int, replyTo: ActorRef[Seq[Attribute]])             extends Command
final case class GetAttributeByName(name: String, replyTo: ActorRef[Option[PersistentAttribute]]) extends Command
case object Idle                                                                                  extends Command

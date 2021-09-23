package it.pagopa.pdnd.interop.userice

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{Marshal, Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.scaladsl.Source
import akka.util.ByteString
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.{Attribute, AttributeSeed, Problem}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.service.impl.UUIDSupplier
import org.scalamock.scalatest.MockFactory

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.api.impl._

package object attributeregistrymanagement extends MockFactory {
  val uuidSupplier: UUIDSupplier              = mock[UUIDSupplier]
  final val authorization: Seq[Authorization] = Seq(headers.Authorization(OAuth2BearerToken("token")))

  implicit def toEntityMarshallerAttributeSeed: ToEntityMarshaller[AttributeSeed] = sprayJsonMarshaller[AttributeSeed]
  implicit def fromEntityUnmarshallerAttribute: FromEntityUnmarshaller[Attribute] = sprayJsonUnmarshaller[Attribute]
  implicit def fromEntityUnmarshallerProblem: FromEntityUnmarshaller[Problem]     = sprayJsonUnmarshaller[Problem]

  def buildPayload[T](
    payload: T
  )(implicit mp: Marshaller[T, MessageEntity], ec: ExecutionContext): Source[ByteString, Any] =
    Await.result(Marshal(payload).to[MessageEntity].map(_.dataBytes), Duration.Inf)

  def createAttribute(data: Source[ByteString, Any])(implicit actorSystem: ActorSystem): HttpResponse =
    execute("attributes", HttpMethods.POST, HttpEntity(ContentTypes.`application/json`, data))

  private def execute(path: String, verb: HttpMethod, data: RequestEntity)(implicit
    actorSystem: ActorSystem
  ): HttpResponse = {
    Await.result(
      Http().singleRequest(
        HttpRequest(
          uri = s"${AkkaTestConfiguration.serviceURL}/$path",
          method = verb,
          entity = data,
          headers = authorization
        )
      ),
      Duration.Inf
    )
  }
}

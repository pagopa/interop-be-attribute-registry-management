package it.pagopa.interop

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{Marshal, Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.scaladsl.Source
import akka.util.ByteString
import it.pagopa.interop.partyregistryproxy.client.model.{Categories, Category}
import it.pagopa.interop.attributeregistrymanagement.api.impl._
import it.pagopa.interop.attributeregistrymanagement.model.{Attribute, AttributeSeed, Problem}
import it.pagopa.interop.attributeregistrymanagement.service.PartyRegistryService
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import org.scalamock.scalatest.MockFactory

import java.net.InetAddress
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

package object attributeregistrymanagement extends MockFactory {
  val uuidSupplier: UUIDSupplier                     = mock[UUIDSupplier]
  val timeSupplier: OffsetDateTimeSupplier           = mock[OffsetDateTimeSupplier]
  val mockPartyRegistryService: PartyRegistryService = mock[PartyRegistryService]

  (mockPartyRegistryService.getCategories _)
    .expects(*)
    .returning(Future.successful(Categories(Seq(Category("YADA", "Proxied", "test", "IPA")))))
    .anyNumberOfTimes()

  final val requestHeaders: Seq[HttpHeader] =
    Seq(
      headers.Authorization(OAuth2BearerToken("token")),
      headers.RawHeader("X-Correlation-Id", "test-id"),
      headers.`X-Forwarded-For`(RemoteAddress(InetAddress.getByName("127.0.0.1")))
    )

  implicit def toEntityMarshallerAttributeSeed: ToEntityMarshaller[AttributeSeed] = sprayJsonMarshaller[AttributeSeed]
  implicit def fromEntityUnmarshallerAttribute: FromEntityUnmarshaller[Attribute] = sprayJsonUnmarshaller[Attribute]
  implicit def fromEntityUnmarshallerProblem: FromEntityUnmarshaller[Problem]     = sprayJsonUnmarshaller[Problem]

  def buildPayload[T](
    payload: T
  )(implicit mp: Marshaller[T, MessageEntity], ec: ExecutionContext): Source[ByteString, Any] =
    Await.result(Marshal(payload).to[MessageEntity].map(_.dataBytes), Duration.Inf)

  def createAttribute(data: Source[ByteString, Any])(implicit actorSystem: ActorSystem): HttpResponse =
    execute("attributes", HttpMethods.POST, HttpEntity(ContentTypes.`application/json`, data))

  def loadAttributes(implicit actorSystem: ActorSystem): HttpResponse =
    Await.result(
      Http().singleRequest(
        HttpRequest(
          uri = s"${AkkaTestConfiguration.serviceURL}/jobs/attributes/certified/load",
          method = HttpMethods.POST,
          headers = requestHeaders
        )
      ),
      Duration.Inf
    )

  def findAttributeByName(name: String)(implicit actorSystem: ActorSystem): HttpResponse =
    Await.result(
      Http().singleRequest(
        HttpRequest(
          uri = s"${AkkaTestConfiguration.serviceURL}/attributes/name/$name",
          method = HttpMethods.GET,
          headers = requestHeaders
        )
      ),
      Duration.Inf
    )

  def findAttributeByOriginAndCode(origin: String, code: String)(implicit actorSystem: ActorSystem): HttpResponse =
    Await.result(
      Http().singleRequest(
        HttpRequest(
          uri = s"${AkkaTestConfiguration.serviceURL}/attributes/origin/$origin/code/$code",
          method = HttpMethods.GET,
          headers = requestHeaders
        )
      ),
      Duration.Inf
    )

  private def execute(path: String, verb: HttpMethod, data: RequestEntity)(implicit
    actorSystem: ActorSystem
  ): HttpResponse = {
    Await.result(
      Http().singleRequest(
        HttpRequest(
          uri = s"${AkkaTestConfiguration.serviceURL}/$path",
          method = verb,
          entity = data,
          headers = requestHeaders
        )
      ),
      Duration.Inf
    )
  }
}

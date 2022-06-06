package it.pagopa.interop

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{Marshal, ToEntityMarshaller}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal, Unmarshaller}
import it.pagopa.interop.partyregistryproxy.client.model.{Categories, Category}
import it.pagopa.interop.attributeregistrymanagement.api.impl._
import it.pagopa.interop.attributeregistrymanagement.model.{Attribute, AttributeSeed, Problem}
import it.pagopa.interop.attributeregistrymanagement.service.PartyRegistryService
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import org.scalamock.scalatest.MockFactory

import java.net.InetAddress
import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID
import java.time.OffsetDateTime

package object attributeregistrymanagement extends MockFactory {
  implicit val contexts: Seq[(String, String)]       = Seq.empty
  val uuidSupplier: UUIDSupplier                     = new UUIDSupplier { def get = UUID.randomUUID() }
  val timeSupplier: OffsetDateTimeSupplier           = new OffsetDateTimeSupplier { def get = OffsetDateTime.now() }
  val mockPartyRegistryService: PartyRegistryService = mock[PartyRegistryService]

  (mockPartyRegistryService
    .getCategories(_: String)(_: Seq[(String, String)]))
    .expects(*, *)
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

  def createAttribute[T](
    seed: AttributeSeed
  )(implicit actorSystem: ActorSystem[_], mf: Unmarshaller[ResponseEntity, T]): Future[(StatusCode, T)] = {
    implicit val ec: ExecutionContext = actorSystem.executionContext
    for {
      data     <- Marshal(seed).to[MessageEntity].map(_.dataBytes)
      response <- execute("attributes", POST, HttpEntity(ContentTypes.`application/json`, data))
      body     <- Unmarshal(response.entity).to[T]
    } yield (response.status, body)
  }

  def createBulk(
    seeds: List[AttributeSeed]
  )(implicit actorSystem: ActorSystem[_]): Future[(StatusCode, List[Attribute])] = {
    implicit val ec: ExecutionContext = actorSystem.executionContext
    for {
      data     <- Marshal(seeds).to[MessageEntity].map(_.dataBytes)
      response <- execute("bulk/attributes", POST, HttpEntity(ContentTypes.`application/json`, data))
      body     <- Unmarshal(response.entity).to[List[Attribute]]
    } yield (response.status, body)
  }

  def getAllAttributes(implicit actorSystem: ActorSystem[_]): Future[(StatusCode, List[Attribute])] = {
    implicit val ec: ExecutionContext = actorSystem.executionContext
    for {
      response <- execute("attributes", GET)
      body     <- Unmarshal(response.entity).to[List[Attribute]]
    } yield (response.status, body)
  }

  def deleteAttribute(attributeId: String)(implicit actorSystem: ActorSystem[_]): Future[StatusCode] = {
    implicit val ec: ExecutionContext = actorSystem.executionContext
    execute(s"attributes/$attributeId", DELETE).map(_.status)
  }

  def loadAttributes(implicit actorSystem: ActorSystem[_]): Future[StatusCode] = {
    implicit val ec: ExecutionContext = actorSystem.executionContext
    execute("jobs/attributes/certified/load", POST).map(_.status)
  }

  def findAttributeByName(name: String)(implicit actorSystem: ActorSystem[_]): Future[(StatusCode, Attribute)] = {
    implicit val ec: ExecutionContext = actorSystem.executionContext
    for {
      response <- execute(s"attributes/name/$name", GET)
      body     <- Unmarshal(response.entity).to[Attribute]
    } yield (response.status, body)
  }

  def findAttributeByOriginAndCode(origin: String, code: String)(implicit
    actorSystem: ActorSystem[_]
  ): Future[(StatusCode, Attribute)] = {
    implicit val ec: ExecutionContext = actorSystem.executionContext
    for {
      response <- execute(s"attributes/origin/$origin/code/$code", GET)
      body     <- Unmarshal(response.entity).to[Attribute]
    } yield (response.status, body)
  }

  private def execute(path: String, verb: HttpMethod, data: RequestEntity)(implicit
    actorSystem: ActorSystem[_]
  ): Future[HttpResponse] = Http().singleRequest(
    HttpRequest(
      uri = s"${AkkaTestConfiguration.serviceURL}/$path",
      method = verb,
      entity = data,
      headers = requestHeaders
    )
  )

  private def execute(path: String, verb: HttpMethod)(implicit actorSystem: ActorSystem[_]): Future[HttpResponse] =
    Http().singleRequest(
      HttpRequest(uri = s"${AkkaTestConfiguration.serviceURL}/$path", method = verb, headers = requestHeaders)
    )

}

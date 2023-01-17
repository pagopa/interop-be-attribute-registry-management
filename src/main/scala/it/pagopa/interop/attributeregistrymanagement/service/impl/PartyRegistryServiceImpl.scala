package it.pagopa.interop.attributeregistrymanagement.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.attributeregistrymanagement.service.{PartyProxyInvoker, PartyRegistryService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.withHeaders
import it.pagopa.interop.partyregistryproxy.client.api.CategoryApi
import it.pagopa.interop.partyregistryproxy.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.interop.partyregistryproxy.client.model.Categories

import scala.concurrent.Future

final case class PartyRegistryServiceImpl(invoker: PartyProxyInvoker, api: CategoryApi) extends PartyRegistryService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getCategories(bearerToken: String)(implicit contexts: Seq[(String, String)]): Future[Categories] =
    withHeaders { (bearerToken, correlationId, ip) =>
      val request: ApiRequest[Categories] =
        api.getCategories(
          origin = None,
          page = Some(1),
          limit = Some(100),
          xCorrelationId = correlationId,
          xForwardedFor = ip
        )(BearerToken(bearerToken))
      invoker.invoke(request, "Retrieving categories")
    }

}

package it.pagopa.interop.attributeregistrymanagement.service.impl

import it.pagopa.interop.attributeregistrymanagement.service.{PartyProxyInvoker, PartyRegistryService}
import it.pagopa.interop.partyregistryproxy.client.api.CategoryApi
import it.pagopa.interop.partyregistryproxy.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.interop.partyregistryproxy.client.model.Categories
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}

import scala.concurrent.Future

final case class PartyRegistryServiceImpl(invoker: PartyProxyInvoker, api: CategoryApi) extends PartyRegistryService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getCategories(bearerToken: String)(implicit contexts: Seq[(String, String)]): Future[Categories] = {
    val request: ApiRequest[Categories] = api.getCategories(origin = None)(BearerToken(bearerToken))
    logger.info(s"getCategories ${request.toString}")
    invoker.invoke(request, "Retrieving categories")
  }

}

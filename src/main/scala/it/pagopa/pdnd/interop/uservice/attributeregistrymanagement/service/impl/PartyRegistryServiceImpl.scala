package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.service.impl

import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.service.{PartyProxyInvoker, PartyRegistryService}
import it.pagopa.pdnd.interop.uservice.partyregistryproxy.client.api.CategoryApi
import it.pagopa.pdnd.interop.uservice.partyregistryproxy.client.invoker.ApiRequest
import it.pagopa.pdnd.interop.uservice.partyregistryproxy.client.model.Categories
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Future

final case class PartyRegistryServiceImpl(invoker: PartyProxyInvoker, api: CategoryApi) extends PartyRegistryService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getCategories(bearerToken: String): Future[Categories] = {
    val request: ApiRequest[Categories] = api.getCategories(origin = None)
    logger.info(s"getCategories ${request.toString}")
    invoker.invoke(request, "Retrieving categories")
  }

}
package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.service

import it.pagopa.pdnd.interop.uservice.partyregistryproxy.client.model.Categories

import scala.concurrent.Future

trait PartyRegistryService {
  def getCategories(bearerToken: String): Future[Categories]
}

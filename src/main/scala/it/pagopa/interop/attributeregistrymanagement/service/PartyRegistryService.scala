package it.pagopa.interop.attributeregistrymanagement.service

import it.pagopa.interop.partyregistryproxy.client.model.Categories

import scala.concurrent.Future

trait PartyRegistryService {
  def getCategories(bearerToken: String): Future[Categories]
}

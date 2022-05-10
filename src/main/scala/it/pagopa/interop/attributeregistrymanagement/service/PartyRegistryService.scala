package it.pagopa.interop.attributeregistrymanagement.service

import it.pagopa.interop.partyregistryproxy.client.model.Categories

import scala.concurrent.Future

trait PartyRegistryService {
  def getCategories(bearerToken: String)(implicit contexts: Seq[(String, String)]): Future[Categories]
}

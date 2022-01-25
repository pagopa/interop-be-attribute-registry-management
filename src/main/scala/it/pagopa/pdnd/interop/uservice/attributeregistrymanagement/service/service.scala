package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement

import akka.actor.ActorSystem
import it.pagopa.pdnd.interop.uservice.partyregistryproxy.client.invoker.ApiInvoker

package object service {
  type PartyProxyInvoker = ApiInvoker
  object PartyProxyInvoker {
    def apply()(implicit actorSystem: ActorSystem): PartyProxyInvoker = ApiInvoker()
  }

}

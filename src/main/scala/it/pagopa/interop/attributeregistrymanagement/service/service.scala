package it.pagopa.interop.attributeregistrymanagement

import akka.actor.ActorSystem
import it.pagopa.interop.partyregistryproxy.client.invoker.ApiInvoker

package object service {
  type PartyProxyInvoker = ApiInvoker
  object PartyProxyInvoker {
    def apply()(implicit actorSystem: ActorSystem): PartyProxyInvoker = ApiInvoker()
  }

}

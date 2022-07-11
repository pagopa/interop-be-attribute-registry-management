package it.pagopa.interop.attributeregistrymanagement

import akka.actor.ActorSystem
import it.pagopa.interop.partyregistryproxy.client.invoker.ApiInvoker
import scala.concurrent.ExecutionContextExecutor

package object service {
  type PartyProxyInvoker = ApiInvoker
  object PartyProxyInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): PartyProxyInvoker =
      ApiInvoker()(actorSystem, blockingEc)
  }

}

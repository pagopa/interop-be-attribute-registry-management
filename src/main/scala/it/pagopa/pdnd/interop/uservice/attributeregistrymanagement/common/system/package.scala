package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.common

import akka.util.Timeout

import scala.concurrent.duration.DurationInt

package object system {

  implicit val timeout: Timeout = 300.seconds

}

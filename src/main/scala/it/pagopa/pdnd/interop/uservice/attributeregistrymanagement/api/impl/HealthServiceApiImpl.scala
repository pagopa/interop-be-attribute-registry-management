package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Route
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.api.HealthApiService
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.Problem

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class HealthServiceApiImpl extends HealthApiService {

  override def getStatus()(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = getStatus200(
    Problem(None, 200, "OK")
  )

}

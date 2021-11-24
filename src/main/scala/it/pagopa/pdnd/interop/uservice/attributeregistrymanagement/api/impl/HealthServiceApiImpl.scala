package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Route
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.api.HealthApiService
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.Problem

class HealthServiceApiImpl extends HealthApiService {

  /** Code: 200, Message: successful operation, DataType: Problem
    */
  override def getStatus()(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = getStatus200(Problem(None, 200, "OK"))

}

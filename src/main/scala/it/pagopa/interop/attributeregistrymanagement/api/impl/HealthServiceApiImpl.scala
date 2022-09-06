package it.pagopa.interop.attributeregistrymanagement.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import it.pagopa.interop.attributeregistrymanagement.api.HealthApiService
import it.pagopa.interop.attributeregistrymanagement.model.Problem

class HealthServiceApiImpl extends HealthApiService {

  /** Code: 200, Message: successful operation, DataType: Problem
    */
  override def getStatus()(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val response: Problem = problemOf(StatusCodes.OK, List.empty)
    complete(response.status, response)
  }

}

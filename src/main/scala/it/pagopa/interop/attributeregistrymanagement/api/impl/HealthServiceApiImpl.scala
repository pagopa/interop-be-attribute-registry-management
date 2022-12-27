package it.pagopa.interop.attributeregistrymanagement.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import it.pagopa.interop.attributeregistrymanagement.api.HealthApiService
import it.pagopa.interop.attributeregistrymanagement.model.{Problem => LocalProblem}
import it.pagopa.interop.commons.utils.errors.{Problem, ProblemError}

class HealthServiceApiImpl extends HealthApiService {

  override def getStatus()(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[LocalProblem]
  ): Route = {
    val response: Problem = Problem(
      `type` = "about:blank",
      status = StatusCodes.OK.intValue,
      title = StatusCodes.OK.defaultMessage,
      correlationId = None,
      errors = Seq.empty[ProblemError]
    )
    complete(response.status, response)
  }

}

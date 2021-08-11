package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.api.HealthApiMarshaller
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.Problem

class HealthApiMarshallerImpl extends HealthApiMarshaller {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] =
    sprayJsonMarshaller[Problem](jsonFormat3(Problem))
}

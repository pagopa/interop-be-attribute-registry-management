package it.pagopa.interop.attributeregistrymanagement.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.attributeregistrymanagement.api.HealthApiMarshaller
import it.pagopa.interop.attributeregistrymanagement.model.Problem

class HealthApiMarshallerImpl extends HealthApiMarshaller {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem
}

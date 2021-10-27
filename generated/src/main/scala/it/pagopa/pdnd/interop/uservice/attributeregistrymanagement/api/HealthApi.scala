package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.api

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive1, Route}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.http.scaladsl.unmarshalling.FromStringUnmarshaller
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.server.AkkaHttpHelper._
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.Problem


    class HealthApi(
    healthService: HealthApiService,
    healthMarshaller: HealthApiMarshaller,
    wrappingDirective: Directive1[Unit]
    ) {
    
    import healthMarshaller._

    lazy val route: Route =
        path("status") { 
        get { wrappingDirective { _ =>  
            healthService.getStatus()
          }
        }
        }
    }


    trait HealthApiService {
          def getStatus200(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((200, responseProblem))
        /**
           * Code: 200, Message: successful operation, DataType: Problem
        */
        def getStatus()
            (implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route

    }

        trait HealthApiMarshaller {
        
        
          implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem]

        }


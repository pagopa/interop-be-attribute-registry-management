package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.api

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive1, Route}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.http.scaladsl.unmarshalling.FromStringUnmarshaller
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.server.AkkaHttpHelper._
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.Attribute
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.AttributeSeed
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.AttributesResponse
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.model.Problem
import java.util.UUID


    class AttributeApi(
    attributeService: AttributeApiService,
    attributeMarshaller: AttributeApiMarshaller,
    wrappingDirective: Directive1[Unit]
    ) {
    
    import attributeMarshaller._

    lazy val route: Route =
        path("attributes") { 
        post { wrappingDirective { _ =>  
            entity(as[AttributeSeed]){ attributeSeed =>
              attributeService.createAttribute(attributeSeed = attributeSeed)
            }
          }
        }
        } ~
        path("bulk" / "attributes") { 
        post { wrappingDirective { _ =>  
            entity(as[Seq[AttributeSeed]]){ attributeSeed =>
              attributeService.createAttributes(attributeSeed = attributeSeed)
            }
          }
        }
        } ~
        path("attributes" / Segment) { (attributeId) => 
        get { wrappingDirective { _ =>  
            attributeService.getAttributeById(attributeId = attributeId)
          }
        }
        } ~
        path("attributes" / "name" / Segment) { (name) => 
        get { wrappingDirective { _ =>  
            attributeService.getAttributeByName(name = name)
          }
        }
        } ~
        path("attributes") { 
        get { wrappingDirective { _ => 
            parameters("search".as[String].?) { (search) => 
            attributeService.getAttributes(search = search)
            }
          }
        }
        } ~
        path("bulk" / "attributes") { 
        get { wrappingDirective { _ => 
            parameters("ids".as[String].?) { (ids) => 
            attributeService.getBulkedAttributes(ids = ids)
            }
          }
        }
        }
    }


    trait AttributeApiService {
          def createAttribute201(responseAttribute: Attribute)(implicit toEntityMarshallerAttribute: ToEntityMarshaller[Attribute]): Route =
            complete((201, responseAttribute))
  def createAttribute400(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((400, responseProblem))
        /**
           * Code: 201, Message: Attribute created, DataType: Attribute
   * Code: 400, Message: Bad Request, DataType: Problem
        */
        def createAttribute(attributeSeed: AttributeSeed)
            (implicit toEntityMarshallerAttribute: ToEntityMarshaller[Attribute], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route

          def createAttributes201(responseAttributesResponse: AttributesResponse)(implicit toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse]): Route =
            complete((201, responseAttributesResponse))
  def createAttributes400(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((400, responseProblem))
        /**
           * Code: 201, Message: Array of created attributes and already exising ones..., DataType: AttributesResponse
   * Code: 400, Message: Bad Request, DataType: Problem
        */
        def createAttributes(attributeSeed: Seq[AttributeSeed])
            (implicit toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route

          def getAttributeById200(responseAttribute: Attribute)(implicit toEntityMarshallerAttribute: ToEntityMarshaller[Attribute]): Route =
            complete((200, responseAttribute))
  def getAttributeById404(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((404, responseProblem))
        /**
           * Code: 200, Message: Attribute data, DataType: Attribute
   * Code: 404, Message: Attribute not found, DataType: Problem
        */
        def getAttributeById(attributeId: String)
            (implicit toEntityMarshallerAttribute: ToEntityMarshaller[Attribute], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route

          def getAttributeByName200(responseAttribute: Attribute)(implicit toEntityMarshallerAttribute: ToEntityMarshaller[Attribute]): Route =
            complete((200, responseAttribute))
  def getAttributeByName404(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((404, responseProblem))
        /**
           * Code: 200, Message: Attribute data, DataType: Attribute
   * Code: 404, Message: Attribute not found, DataType: Problem
        */
        def getAttributeByName(name: String)
            (implicit toEntityMarshallerAttribute: ToEntityMarshaller[Attribute], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route

          def getAttributes200(responseAttributesResponse: AttributesResponse)(implicit toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse]): Route =
            complete((200, responseAttributesResponse))
  def getAttributes404(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((404, responseProblem))
        /**
           * Code: 200, Message: array of currently available attributes, DataType: AttributesResponse
   * Code: 404, Message: Attributes not found, DataType: Problem
        */
        def getAttributes(search: Option[String])
            (implicit toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route

          def getBulkedAttributes200(responseAttributesResponse: AttributesResponse)(implicit toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse]): Route =
            complete((200, responseAttributesResponse))
        /**
           * Code: 200, Message: array of attributes, DataType: AttributesResponse
        */
        def getBulkedAttributes(ids: Option[String])
            (implicit toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse]): Route

    }

        trait AttributeApiMarshaller {
          implicit def fromEntityUnmarshallerAttributeSeed: FromEntityUnmarshaller[AttributeSeed]

  implicit def fromEntityUnmarshallerAttributeSeedList: FromEntityUnmarshaller[Seq[AttributeSeed]]


        
          implicit def toEntityMarshallerAttributesResponse: ToEntityMarshaller[AttributesResponse]

  implicit def toEntityMarshallerAttribute: ToEntityMarshaller[Attribute]

  implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem]

        }


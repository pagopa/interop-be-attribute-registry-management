package it.pagopa.interop.attributeregistrymanagement.util

import it.pagopa.interop.commons.jwt.{ADMIN_ROLE, API_ROLE, INTERNAL_ROLE, M2M_ROLE, SECURITY_ROLE}
import akka.http.scaladsl.client.RequestBuilding.{Delete, Get, Post, Put}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpRequest
import it.pagopa.interop.commons.utils.USER_ROLES
import spray.json.DefaultJsonProtocol._
import spray.json._
import java.util.UUID

case class Endpoints(endpoints: Set[Endpoint])
case class Endpoint(route: String, verb: String, roles: Seq[String]) {
  import AuthorizedRoutes._
  def invalidRoles: Seq[Seq[(String, String)]]    = existingRoles.diff(roles).map(toHeaders)
  def rolesInContexts: Seq[Seq[(String, String)]] = roles.map(toHeaders)
  def asRequest: HttpRequest                      = verb match {
    case "GET"    => Get()
    case "POST"   => Post()
    case "DELETE" => Delete()
    case "PUT"    => Put()
  }
}

object AuthorizedRoutes extends SprayJsonSupport {
  val toHeaders: String => Seq[(String, String)] = role =>
    Seq("bearer" -> "token", "uid" -> UUID.randomUUID().toString, USER_ROLES -> role)
  val existingRoles: Seq[String]                 = Seq(ADMIN_ROLE, SECURITY_ROLE, API_ROLE, M2M_ROLE, INTERNAL_ROLE)
  private val lines: String                      = scala.io.Source.fromResource("authz.json").getLines().mkString
  private implicit val endpointFormat: RootJsonFormat[Endpoint]   = jsonFormat3(Endpoint)
  private implicit val endpointsFormat: RootJsonFormat[Endpoints] = jsonFormat1(Endpoints)
  val endpoints: Map[String, Endpoint] = lines.parseJson.convertTo[Endpoints].endpoints.map(e => e.route -> e).toMap
}

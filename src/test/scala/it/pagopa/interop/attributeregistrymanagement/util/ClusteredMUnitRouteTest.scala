package it.pagopa.interop.attributeregistrymanagement.util

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTest
import it.pagopa.interop.attributeregistrymanagement.model.persistence.Command
import it.pagopa.interop.commons.utils.USER_ROLES
import munit.FunSuite

import scala.concurrent.duration._
import munit.Location

import akka.http.scaladsl.testkit.TestFrameworkInterface
import akka.http.scaladsl.server.ExceptionHandler
import it.pagopa.interop.attributeregistrymanagement.AkkaTestConfiguration

trait ClusteredMUnitRouteTest extends FunSuite with RouteTest with TestFrameworkInterface {

  val testPersistentEntity: Entity[Command, ShardingEnvelope[Command]]

  override def afterAll() = {
    ActorTestKit.shutdown(testTypedSystem, 10.seconds)
    cleanUp()
  }

  override def beforeAll(): Unit = {
    val _ = testAkkaSharding.init(testPersistentEntity)
  }

  override def failTest(msg: String): Nothing = fail(msg)
  def testExceptionHandler: ExceptionHandler  = ExceptionHandler { case e => throw e }

  lazy val testKit             = ActorTestKit(AkkaTestConfiguration.config)
  implicit def testTypedSystem = testKit.system

  val testAkkaSharding: ClusterSharding = ClusterSharding(testTypedSystem)
  Cluster(testTypedSystem).manager ! Join(Cluster(testTypedSystem).selfMember.address)

  def validateAuthorization(endpoint: Endpoint, r: Seq[(String, String)] => Route)(implicit loc: Location): Unit = {
    endpoint.rolesInContexts.foreach(contexts => {
      validRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, r(contexts))
    })

    // given a fake role, check that its invocation is forbidden
    endpoint.invalidRoles.foreach(contexts => {
      invalidRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, r(contexts))
    })
  }

  // when request occurs, check that it does not return neither 401 nor 403
  private def validRoleCheck(role: String, request: => HttpRequest, r: => Route)(implicit loc: Location) =
    request ~> r ~> check {
      assertNotEquals(status, StatusCodes.Unauthorized, s"role $role should not be unauthorized")
      assertNotEquals(status, StatusCodes.Forbidden, s"role $role should not be forbidden")
    }

  // when request occurs, check that it forbids invalid role
  private def invalidRoleCheck(role: String, request: => HttpRequest, r: => Route)(implicit loc: Location) = {
    request ~> r ~> check {
      assertEquals(status, StatusCodes.Forbidden, s"role $role should be forbidden")
    }
  }
}

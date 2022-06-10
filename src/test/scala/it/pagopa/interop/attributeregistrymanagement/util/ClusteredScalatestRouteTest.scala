package it.pagopa.interop.attributeregistrymanagement.util

import akka.actor
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

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

trait MUnitRouteTest extends RouteTest with MUnitTestFrameworkInterface { this: munit.FunSuite => }

trait ClusteredScalatestRouteTest extends MUnitRouteTest {
  suite: FunSuite =>
  // the Akka HTTP route testkit does not yet support a typed actor system (https://github.com/akka/akka-http/issues/2036)
  // so we have to adapt for now
  lazy val testKit                                         = ActorTestKit()
  implicit def testTypedSystem                             = testKit.system
  override def createActorSystem(): akka.actor.ActorSystem =
    testKit.system.classicSystem

  val testAkkaSharding: ClusterSharding = ClusterSharding(testTypedSystem)

  implicit val executionContext: ExecutionContextExecutor = testTypedSystem.executionContext
  val classicSystem: actor.ActorSystem                    = testTypedSystem.classicSystem

  val testPersistentEntity: Entity[Command, ShardingEnvelope[Command]]

  Cluster(testTypedSystem).manager ! Join(Cluster(testTypedSystem).selfMember.address)

  override def beforeAll(): Unit = {
    val _ = testAkkaSharding.init(testPersistentEntity)
  }

  override def afterAll(): Unit = {
    ActorTestKit.shutdown(testTypedSystem, 10.seconds)
    super.afterAll()
  }

  def validateAuthorization(endpoint: Endpoint, r: Seq[(String, String)] => Route): Unit = {
    endpoint.rolesInContexts.foreach(contexts => {
      validRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, r(contexts))
    })

    // given a fake role, check that its invocation is forbidden
    endpoint.invalidRoles.foreach(contexts => {
      invalidRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, r(contexts))
    })
  }

  // when request occurs, check that it does not return neither 401 nor 403
  private def validRoleCheck(role: String, request: => HttpRequest, r: => Route) =
    request ~> r ~> check {
      assertNotEquals(status, StatusCodes.Unauthorized, s"role $role should not be unauthorized")
      assertNotEquals(status, StatusCodes.Forbidden, s"role $role should not be forbidden")
    }

  // when request occurs, check that it forbids invalid role
  private def invalidRoleCheck(role: String, request: => HttpRequest, r: => Route) = {
    request ~> r ~> check {
      assertEquals(status, StatusCodes.Forbidden, s"role $role should be forbidden")
    }
  }
}

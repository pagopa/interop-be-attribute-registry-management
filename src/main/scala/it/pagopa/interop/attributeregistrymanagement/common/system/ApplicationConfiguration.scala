package it.pagopa.interop.attributeregistrymanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  val config: Config = ConfigFactory.load()

  val serverPort: Int             = config.getInt("attribute-registry-management.port")
  val numberOfProjectionTags: Int = config.getInt("akka.cluster.sharding.number-of-shards")

  def projectionTag(index: Int) = s"interop-be-attribute-management-persistence|$index"

  val jwtAudience: Set[String] =
    config.getString("attribute-registry-management.jwt.audience").split(",").toSet.filter(_.nonEmpty)

  val partyProxyUrl: String = config.getString("services.party-proxy")

  val projectionsEnabled: Boolean = config.getBoolean("akka.projection.enabled")

  require(jwtAudience.nonEmpty, "Audience cannot be empty")
}

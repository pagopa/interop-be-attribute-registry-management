package it.pagopa.interop.attributeregistrymanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters.ListHasAsScala

object ApplicationConfiguration {

  lazy val config: Config = ConfigFactory.load()

  lazy val serverPort: Int             = config.getInt("attribute-registry-management.port")
  lazy val numberOfProjectionTags: Int = config.getInt("attribute-registry-management.projection-tags")

  def projectionTag(index: Int) = s"interop-be-attribute-management-persistence|$index"

  lazy val jwtAudience: Set[String] =
    config.getStringList("attribute-registry-management.jwt.audience").asScala.toSet

  lazy val partyProxyUrl: String = config.getString("services.party-proxy")

}

package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {

  lazy val config: Config = ConfigFactory.load()

  def serverPort: Int            = config.getInt("pdnd-interop-uservice-attribute-registry-management.port")
  lazy val partyProxyUrl: String = config.getString("services.party-proxy")

}

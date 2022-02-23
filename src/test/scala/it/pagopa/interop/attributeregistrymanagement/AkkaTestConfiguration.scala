package it.pagopa.interop.attributeregistrymanagement

import com.typesafe.config.{Config, ConfigFactory}

/** Selfless trait containing base test configuration for Akka Cluster Setup
  */
trait AkkaTestConfiguration {
  val testData: Config = ConfigFactory.parseString(s"""
      akka.actor.provider = cluster

      akka.remote.classic.netty.tcp.port = 0
      akka.remote.artery.canonical.port = 0
      akka.remote.artery.canonical.hostname = 127.0.0.1

      akka.cluster.jmx.multi-mbeans-in-same-jvm = on

      akka.cluster.sharding.number-of-shards = 10

      akka.coordinated-shutdown.terminate-actor-system = off
      akka.coordinated-shutdown.run-by-actor-system-terminate = off
      akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      akka.cluster.run-coordinated-shutdown-when-down = off
    """)

  val config: Config = ConfigFactory
    .parseResourcesAnySyntax("test")
    .withFallback(testData)

  def serviceURL: String = s"${config.getString("application.url")}${buildinfo.BuildInfo.interfaceVersion}"
}

object AkkaTestConfiguration extends AkkaTestConfiguration

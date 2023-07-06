package it.pagopa.interop.attributeregistrymanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}
import it.pagopa.interop.commons.cqrs.model.MongoDbConfig

object ApplicationConfiguration {
  val config: Config = ConfigFactory.load()

  val serverPort: Int             = config.getInt("attribute-registry-management.port")
  val numberOfProjectionTags: Int = config.getInt("akka.cluster.sharding.number-of-shards")

  def projectionTag(index: Int) = s"interop-be-attribute-management-persistence|$index"

  val jwtAudience: Set[String] =
    config.getString("attribute-registry-management.jwt.audience").split(",").toSet.filter(_.nonEmpty)

  val projectionsEnabled: Boolean = config.getBoolean("akka.projection.enabled")

  // Loaded only if projections are enabled
  lazy val mongoDb: MongoDbConfig = {
    val connectionString: String = config.getString("cqrs-projection.db.connection-string")
    val dbName: String           = config.getString("cqrs-projection.db.name")
    val collectionName: String   = config.getString("cqrs-projection.db.collection-name")

    MongoDbConfig(connectionString, dbName, collectionName)
  }

  require(jwtAudience.nonEmpty, "Audience cannot be empty")
}

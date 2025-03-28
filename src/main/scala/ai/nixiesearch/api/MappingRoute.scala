package ai.nixiesearch.api

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.sync.Index
import cats.effect.IO
import org.http4s.circe.*
import org.http4s.{EntityEncoder, HttpRoutes}
import org.http4s.dsl.io.*

case class MappingRoute(index: Index) extends Route with Logging {
  import MappingRoute.given

  val routes = HttpRoutes.of[IO] {
    case GET -> Root / "v1" / "index" / indexName if index.mapping.nameMatches(indexName) => Ok(index.mapping)
    // legacy
    case GET -> Root / indexName / "_mapping" if index.mapping.nameMatches(indexName) => Ok(index.mapping)
  }
}

object MappingRoute {
  import IndexMapping.json.given
  given mappingJson: EntityEncoder[IO, IndexMapping] = jsonEncoderOf
}

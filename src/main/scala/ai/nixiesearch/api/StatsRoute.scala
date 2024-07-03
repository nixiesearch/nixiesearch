package ai.nixiesearch.api

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.{IndexStats, Searcher}
import ai.nixiesearch.index.sync.Index
import cats.effect.IO
import org.http4s.circe.*
import org.http4s.{EntityEncoder, HttpRoutes}
import org.http4s.dsl.io.*

case class StatsRoute(searcher: Searcher) {
  import StatsRoute.given
  val routes = HttpRoutes.of[IO] {
    case GET -> Root / indexName / "_stats" if indexName == searcher.index.name.value =>
      for {
        readers  <- searcher.getReadersOrFail()
        stats    <- IndexStats.fromIndex(searcher.index.directory, readers.reader)
        response <- Ok(stats)
      } yield {
        response
      }
  }
}

object StatsRoute {
  given statsJson: EntityEncoder[IO, IndexStats] = jsonEncoderOf

}

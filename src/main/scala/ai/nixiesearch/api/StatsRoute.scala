package ai.nixiesearch.api

import ai.nixiesearch.index.{IndexStats, Searcher}
import cats.effect.IO
import org.http4s.circe.*
import org.http4s.{EntityEncoder, HttpRoutes, Response}
import org.http4s.dsl.io.*

case class StatsRoute(searcher: Searcher) extends Route {
  import StatsRoute.given

  override val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "v1" / "index" / indexName / "stats" if searcher.index.mapping.nameMatches(indexName) => stats()
    // legacy
    case GET -> Root / indexName / "_stats" if searcher.index.mapping.nameMatches(indexName) => stats()

  }

  def stats(): IO[Response[IO]] = for {
    readers  <- searcher.getReadersOrFail()
    stats    <- IndexStats.fromIndex(searcher.index.directory, readers.reader)
    response <- Ok(stats)
  } yield {
    response
  }
}

object StatsRoute {
  given statsJson: EntityEncoder[IO, IndexStats] = jsonEncoderOf

}

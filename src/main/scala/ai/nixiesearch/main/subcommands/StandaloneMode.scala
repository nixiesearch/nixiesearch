package ai.nixiesearch.main.subcommands

import ai.nixiesearch.api.*
import ai.nixiesearch.config.Config
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.IndexList
import ai.nixiesearch.index.cluster.{Indexer, Searcher}
import ai.nixiesearch.main.CliConfig.CliArgs.StandaloneArgs
import cats.effect.IO
import cats.implicits.*
import com.comcast.ip4s.{Hostname, Port}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.{ErrorAction, Logger}

import scala.concurrent.duration.Duration

object StandaloneMode extends Logging {
  def run(args: StandaloneArgs): IO[Unit] = for {
    _      <- info("Starting in 'standalone' mode with indexer+searcher colocated within a single process")
    config <- Config.load(args.config)
    _ <- IndexList
      .fromConfig(config)
      .use(indices =>
        for {
          searcher    <- Searcher.open(indices)
          indexer     <- Indexer.create(indices)
          searchRoute <- IO(SearchRoute(searcher))
          indexRoute  <- IO(IndexRoute(indexer))
          healthRoute <- IO(HealthRoute())
          uiRoute     <- WebuiRoute.create(searcher, searchRoute, config)
          routes <- IO(
            searchRoute.routes <+> indexRoute.routes <+> healthRoute.routes <+> uiRoute.routes
          )
          server <- API.start(routes, config)
          _      <- server.use(_ => IO.never)

        } yield {}
      )
  } yield {}
}

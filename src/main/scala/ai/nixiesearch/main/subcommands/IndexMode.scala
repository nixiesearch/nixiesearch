package ai.nixiesearch.main.subcommands

import ai.nixiesearch.api.{API, HealthRoute, IndexRoute, SearchRoute, WebuiRoute}
import ai.nixiesearch.config.Config
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.IndexList
import ai.nixiesearch.index.cluster.{Indexer, Searcher}
import ai.nixiesearch.main.CliConfig.CliArgs.{IndexArgs, StandaloneArgs}
import cats.effect.IO
import cats.implicits.*

object IndexMode extends Logging {
  def run(args: IndexArgs): IO[Unit] = for {
    _      <- info("Starting in 'index' mode with indexer only")
    config <- Config.load(args.config)
    _ <- IndexList
      .fromConfig(config)
      .use(indices =>
        for {
          indexer     <- Indexer.create(indices)
          indexRoute  <- IO(IndexRoute(indexer))
          healthRoute <- IO(HealthRoute())
          routes      <- IO(indexRoute.routes <+> healthRoute.routes)
          server      <- API.start(routes, config)
          _           <- server.use(_ => IO.never)
        } yield {}
      )
  } yield {}

}

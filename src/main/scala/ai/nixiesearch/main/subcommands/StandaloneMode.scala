package ai.nixiesearch.main.subcommands

import ai.nixiesearch.api.*
import ai.nixiesearch.api.API.info
import ai.nixiesearch.config.Config
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.{Indexer, Searcher}
import ai.nixiesearch.index.sync.Index
import ai.nixiesearch.main.CliConfig.CliArgs.StandaloneArgs
import ai.nixiesearch.main.Logo
import cats.effect.IO
import cats.implicits.*

object StandaloneMode extends Logging {
  def run(args: StandaloneArgs): IO[Unit] = for {
    _      <- info("Starting in 'standalone' mode with indexer+searcher colocated within a single process")
    config <- Config.load(args.config)
    _ <- config.schema.values.toList
      .map(im => Index.local(im))
      .sequence
      .use(indexes =>
        indexes
          .map(index => Indexer.open(index))
          .sequence
          .use(indexers =>
            indexes
              .map(index => Searcher.open(index))
              .sequence
              .use(searchers =>
                for {
                  indexRoutes  <- IO(indexers.map(indexer => IndexRoute(indexer).routes).reduce(_ <+> _))
                  searchRoutes <- IO(searchers.map(s => SearchRoute(s).routes <+> WebuiRoute(s).routes).reduce(_ <+> _))
                  health       <- IO(HealthRoute())
                  routes       <- IO(indexRoutes <+> searchRoutes <+> health.routes)
                  server       <- API.start(routes, config.searcher.host, config.searcher.port)
                  _            <- Logo.lines.map(line => info(line)).sequence
                  _            <- server.use(_ => IO.never)
                } yield {}
              )
          )
      )
  } yield {}
}

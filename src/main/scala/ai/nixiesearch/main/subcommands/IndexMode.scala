package ai.nixiesearch.main.subcommands

import ai.nixiesearch.api.{API, HealthRoute, IndexRoute}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.{CacheConfig, Config}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.Indexer
import ai.nixiesearch.index.sync.Index
import ai.nixiesearch.main.CliConfig.CliArgs.IndexArgs
import cats.effect.{IO, Resource}
import cats.implicits.*
import fs2.Stream
import scala.concurrent.duration.*

object IndexMode extends Logging {
  def run(args: IndexArgs): IO[Unit] = for {
    _      <- info("Starting in 'index' mode with indexer only")
    config <- Config.load(args.config)
    _ <- config.search.values.toList
      .map(im =>
        for {
          index   <- Index.forIndexing(im, config.core.cache)
          indexer <- Indexer.open(index)
          _ <- Stream
            .repeatEval(indexer.flush().flatMap {
              case false => IO.unit
              case true  => index.sync()
            })
            .metered(1.second)
            .compile
            .drain
            .background
        } yield { indexer }
      )
      .sequence
      .use(indexers =>
        for {
          indexRoutes <- IO(indexers.map(indexer => IndexRoute(indexer).routes).reduce(_ <+> _))
          healthRoute <- IO(HealthRoute())
          routes      <- IO(indexRoutes <+> healthRoute.routes)
          server      <- API.start(routes, config)
          _           <- server.use(_ => IO.never)

        } yield {}
      )

  } yield {}

  
}

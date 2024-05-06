package ai.nixiesearch.main.subcommands

import ai.nixiesearch.api.{API, HealthRoute, IndexRoute, SearchRoute, WebuiRoute}
import ai.nixiesearch.config.Config
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.{IndexList, NixieIndexWriter}
import ai.nixiesearch.index.sync.ReplicatedIndex
import ai.nixiesearch.main.CliConfig.CliArgs.{IndexArgs, StandaloneArgs}
import cats.effect.{IO, Resource}
import cats.implicits.*
import org.http4s.HttpRoutes

object IndexMode extends Logging {
  def run(args: IndexArgs): IO[Unit] = for {
    _      <- info("Starting in 'index' mode with indexer only")
    config <- Config.load(args.config)
    _ <- IndexList
      .fromConfig(config)
      .use(indices =>
        indexRoutes(indices).use(routes =>
          for {
            healthRoute <- IO(HealthRoute())
            routes      <- IO(routes <+> healthRoute.routes)
            server      <- API.start(routes, config)
            _           <- server.use(_ => IO.never)

          } yield {}
        )
      )
  } yield {}

  def indexRoutes(indices: List[ReplicatedIndex]): Resource[IO, HttpRoutes[IO]] = for {
    indexers <- indices.map(index => NixieIndexWriter.open(index)).sequence
    routes   <- Resource.pure(indexers.map(indexer => IndexRoute(indexer).routes))
  } yield {
    routes.reduce(_ <+> _)
  }
}

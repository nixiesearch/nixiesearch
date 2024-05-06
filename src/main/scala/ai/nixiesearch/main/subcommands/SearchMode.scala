package ai.nixiesearch.main.subcommands

import ai.nixiesearch.api.*
import ai.nixiesearch.config.Config
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.{IndexList, Searcher}
import ai.nixiesearch.index.sync.ReplicatedIndex
import ai.nixiesearch.main.CliConfig.CliArgs.{SearchArgs, StandaloneArgs}
import cats.effect.{IO, Resource}
import cats.implicits.*
import com.comcast.ip4s.{Hostname, Port}
import org.http4s.HttpRoutes
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.{ErrorAction, Logger}

object SearchMode extends Logging {
  def run(args: SearchArgs): IO[Unit] = for {
    _      <- info("Starting in 'search' mode with only searcher")
    config <- Config.load(args.config)
    indices <- IndexList
      .fromConfig(config)
      .use(indices =>
        searchRoutes(indices).use(routes =>
          for {
            health <- IO(HealthRoute())
            routes <- IO(routes <+> health.routes)
            server <- API.start(routes, config)
            _      <- server.use(_ => IO.never)
          } yield {}
        )
      )
  } yield {}

  def searchRoutes(indices: List[ReplicatedIndex]): Resource[IO, HttpRoutes[IO]] = for {
    searchers <- indices.map(index => Searcher.open(index)).sequence
    routes <- Resource.eval(
      searchers
        .map(searcher =>
          for {
            searchRoute <- IO(SearchRoute(searcher))
            uiRoute     <- WebuiRoute.create(searchRoute)
          } yield {
            searchRoute.routes <+> uiRoute.routes
          }
        )
        .sequence
    )
  } yield {
    routes.reduce(_ <+> _)
  }

}

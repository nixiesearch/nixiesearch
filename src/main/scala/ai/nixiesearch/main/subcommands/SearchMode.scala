package ai.nixiesearch.main.subcommands

import ai.nixiesearch.api.*
import ai.nixiesearch.api.API.info
import ai.nixiesearch.config.Config
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.{Models, Searcher}
import ai.nixiesearch.index.sync.Index
import ai.nixiesearch.main.CliConfig.CliArgs.SearchArgs
import ai.nixiesearch.main.Logo
import cats.data.Kleisli
import cats.effect.{IO, Resource}
import cats.implicits.*
import fs2.Stream
import org.http4s.server.websocket.WebSocketBuilder

import scala.concurrent.duration.*

object SearchMode extends Logging {
  def run(args: SearchArgs, config: Config): IO[Unit] = {
    val server = for {
      _      <- Resource.eval(info("Starting in 'search' mode with only searcher"))
      models <- Models.create(config.inference, config.core.cache)
      searchers <- config.schema.values.toList
        .map(im =>
          for {
            index    <- Index.forSearch(im, models)
            searcher <- Searcher.open(index)
            _ <- Stream
              .repeatEval(index.sync().flatMap {
                case false => IO.unit
                case true  => searcher.sync()
              })
              .metered(1.second)
              .compile
              .drain
              .background
          } yield {
            searcher
          }
        )
        .sequence
      searchRoutes = searchers
        .map(s => SearchRoute(s).routes <+> MappingRoute(s.index).routes <+> StatsRoute(s).routes)
        .reduce(_ <+> _)
      searchRoutesWss = (wsb: WebSocketBuilder[IO]) => searchers.map(s => SearchRoute(s).wsroutes(wsb)).reduce(_ <+> _)
      routes = List(
        searchRoutes,
        HealthRoute().routes,
        AdminRoute(config).routes,
        MainRoute().routes,
        TypicalErrorsRoute(searchers.map(_.index.name.value)).routes,
        InferenceRoute(models).routes
      )
        .reduce(_ <+> _)
      api <- API.start(routes, searchRoutesWss, config.searcher.host, config.searcher.port)
      _   <- Resource.eval(Logo.lines.map(line => info(line)).sequence)
    } yield {
      api
    }

    server.use(_ => IO.never)
  }
}

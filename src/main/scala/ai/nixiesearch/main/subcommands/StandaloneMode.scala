package ai.nixiesearch.main.subcommands

import ai.nixiesearch.api.*
import ai.nixiesearch.api.API.info
import ai.nixiesearch.config.Config
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.{Indexer, Models, Searcher}
import ai.nixiesearch.index.sync.Index
import ai.nixiesearch.main.CliConfig.CliArgs.StandaloneArgs
import ai.nixiesearch.main.Logo
import ai.nixiesearch.main.subcommands.util.PeriodicFlushStream
import cats.effect.{IO, Resource}
import cats.implicits.*
import org.http4s.server.websocket.WebSocketBuilder

object StandaloneMode extends Logging {
  def run(args: StandaloneArgs, config: Config): IO[Unit] = {
    val api = for {
      _ <- Resource.eval(info("Starting in 'standalone' mode with indexer+searcher colocated within a single process"))
      models <- Models.create(config.inference, config.core.cache)
      indexes <- config.schema.values.toList
        .map(im => Index.local(im, models))
        .sequence
      indexers <- indexes
        .map(index => Indexer.open(index).flatTap(indexer => PeriodicFlushStream.run(index, indexer)))
        .sequence
      searchers <- indexes.map(index => Searcher.open(index)).sequence
      searchRoutesWss = (wsb: WebSocketBuilder[IO]) => searchers.map(s => SearchRoute(s).wsroutes(wsb)).reduce(_ <+> _)
      routes = List(
        searchers
          .map(s => List(SearchRoute(s).routes, MappingRoute(s.index).routes, StatsRoute(s).routes).reduce(_ <+> _))
          .reduce(_ <+> _),
        indexers.map(indexer => IndexRoute(indexer).routes).reduce(_ <+> _),
        TypicalErrorsRoute(searchers.map(_.index.name.value)).routes,
        AdminRoute(config).routes,
        MainRoute().routes,
        HealthRoute().routes,
        InferenceRoute(models).routes
      ).reduce(_ <+> _)
      server <- API.start(routes, searchRoutesWss, config.searcher.host, config.searcher.port)
      _      <- Resource.eval(Logo.lines.map(line => info(line)).sequence)
    } yield {
      server
    }
    api.use(_ => IO.never)
  }
}

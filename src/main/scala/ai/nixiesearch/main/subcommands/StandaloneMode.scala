package ai.nixiesearch.main.subcommands

import ai.nixiesearch.api.*
import ai.nixiesearch.api.API.info
import ai.nixiesearch.config.Config
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.metrics.Metrics
import ai.nixiesearch.index.{Indexer, Models, Searcher}
import ai.nixiesearch.index.sync.Index
import ai.nixiesearch.main.CliConfig.CliArgs.StandaloneArgs
import ai.nixiesearch.main.Logo
import ai.nixiesearch.main.subcommands.util.PeriodicFlushStream
import cats.effect.{IO, Resource}
import cats.implicits.*
import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.http4s.server.Server

object StandaloneMode extends Logging {
  def run(args: StandaloneArgs, config: Config): IO[Unit] = {
    api(args, config).use(_ => IO.never)
  }

  def api(args: StandaloneArgs, config: Config): Resource[IO, Server] = for {
    _ <- Resource.eval(info("Starting in 'standalone' mode with indexer+searcher colocated within a single process"))
    models <- Models.create(config.inference, config.core.cache)
    indexes <- config.schema.values.toList
      .map(im => Index.local(im, models))
      .sequence
    metrics <- Resource.pure(Metrics())
    indexers <- indexes
      .map(index => Indexer.open(index, metrics).flatTap(indexer => PeriodicFlushStream.run(index, indexer)))
      .sequence
    searchers <- indexes.map(index => Searcher.open(index, metrics)).sequence
    routes = List(
      searchers
        .flatMap(s => List(SearchRoute(s).routes, MappingRoute(s.index).routes, StatsRoute(s).routes)),
      indexers.map(indexer => IndexRoute(indexer).routes),
      List(TypicalErrorsRoute(searchers.map(_.index.name.value)).routes),
      List(AdminRoute(config).routes),
      List(MainRoute().routes),
      List(HealthRoute().routes),
      List(InferenceRoute(models, metrics).routes),
      List(MetricsRoute(metrics).routes)
    ).flatten.reduce(_ <+> _)
    server <- API.start(routes, config.core.host, config.core.port)
    _      <- Resource.eval(Logo.lines.map(line => info(line)).sequence)
  } yield {
    server
  }
}

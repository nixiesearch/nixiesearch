package ai.nixiesearch.main.subcommands

import ai.nixiesearch.api.*
import ai.nixiesearch.api.API.info
import ai.nixiesearch.config.Config
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.metrics.Metrics
import ai.nixiesearch.index.{Indexer, Models, Searcher}
import ai.nixiesearch.index.sync.{Index, LocalIndex}
import ai.nixiesearch.main.CliConfig.CliArgs.StandaloneArgs
import ai.nixiesearch.main.Logo
import ai.nixiesearch.main.subcommands.util.PeriodicEvalStream
import ai.nixiesearch.util.EnvVars
import ai.nixiesearch.util.analytics.AnalyticsReporter
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.http4s.server.Server

object StandaloneMode extends Mode[StandaloneArgs] {
  case class Nixiesearch(
      server: Server,
      models: Models,
      indexes: List[LocalIndex],
      indexers: List[Indexer],
      searchers: List[Searcher]
  )

  def run(args: StandaloneArgs, env: EnvVars): IO[Unit] = for {
    config <- Config.load(args.config, env)
    _      <- AnalyticsReporter.create(config, args.mode).use(_ => api(config).use(_ => IO.never))
  } yield {}

  def api(config: Config): Resource[IO, Nixiesearch] = for {
    _ <- Resource.eval(info("Starting in 'standalone' mode with indexer+searcher colocated within a single process"))

    metrics <- Resource.pure(Metrics())
    models  <- Models.create(config.inference, config.core.cache, metrics)
    indexes <- config.schema.values.toList
      .map(im => Index.local(im, models))
      .sequence
    indexers  <- indexes.map(index => Indexer.open(index, metrics)).sequence
    searchers <- indexes.map(index => Searcher.open(index, metrics)).sequence
    _         <- indexers
      .zip(searchers)
      .map { case (indexer, searcher) =>
        PeriodicEvalStream.run(
          every = indexer.index.mapping.config.indexer.flush.interval,
          action = indexer.flush().flatMap {
            case true  => indexer.index.sync() *> searcher.sync()
            case false => IO.unit
          }
        )
      }
      .sequence
    routes = List(
      searchers
        .flatMap(s => List(SearchRoute(s).routes, MappingRoute(s.index).routes, StatsRoute(s).routes)),
      indexers.zip(searchers).map((indexer, searcher) => IndexModifyRoute(indexer, Some(searcher)).routes),
      List(TypicalErrorsRoute(searchers.map(_.index.name.value)).routes),
      List(AdminRoute(config).routes),
      List(MainRoute().routes),
      List(HealthRoute().routes),
      List(InferenceRoute(models).routes),
      List(MetricsRoute(metrics).routes)
    ).flatten.reduce(_ <+> _)
    server <- API.start(routes, config.core.host, config.core.port)
    _      <- Resource.eval(Logo.lines.map(line => info(line)).sequence)
  } yield {
    Nixiesearch(server = server, models = models, indexes = indexes, indexers = indexers, searchers = searchers)
  }
}

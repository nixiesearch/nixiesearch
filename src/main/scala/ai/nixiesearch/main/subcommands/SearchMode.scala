package ai.nixiesearch.main.subcommands

import ai.nixiesearch.api.*
import ai.nixiesearch.api.API.info
import ai.nixiesearch.config.Config
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.metrics.Metrics
import ai.nixiesearch.index.{Models, Searcher}
import ai.nixiesearch.index.sync.Index
import ai.nixiesearch.main.CliConfig.CliArgs.SearchArgs
import ai.nixiesearch.main.Logo
import ai.nixiesearch.main.subcommands.util.PeriodicEvalStream
import ai.nixiesearch.util.EnvVars
import ai.nixiesearch.util.analytics.AnalyticsReporter
import cats.data.Kleisli
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream
import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.http4s.server.Server

import scala.concurrent.duration.*

object SearchMode extends Mode[SearchArgs] {
  def run(args: SearchArgs, env: EnvVars): IO[Unit] = {
    api(args, env).use(_ => IO.never)
  }

  def api(args: SearchArgs, env: EnvVars): Resource[IO, Server] = for {
    _      <- Resource.eval(info("Starting in 'search' mode with only searcher"))
    config <- Resource.eval(Config.load(args.config, env))
    _      <- AnalyticsReporter.create(config, args.mode)


    metrics   <- Resource.pure(Metrics())
    models    <- Models.create(config.inference, config.core.cache, metrics)
    searchers <- config.schema.values.toList
      .map(im =>
        for {
          index    <- Index.forSearch(im, models)
          searcher <- Searcher.open(index, metrics)
          _        <- PeriodicEvalStream.run(
            every = index.mapping.config.indexer.flush.interval,
            action = index.sync().flatMap {
              case false => IO.unit
              case true  => searcher.sync()
            }
          )
        } yield {
          searcher
        }
      )
      .sequence
    searchRoutes = searchers.map(s => SearchRoute(s).routes <+> MappingRoute(s.index).routes <+> StatsRoute(s).routes)
    routes       = List(
      searchRoutes,
      List(HealthRoute().routes),
      List(AdminRoute(config).routes),
      List(MainRoute().routes),
      List(TypicalErrorsRoute(searchers.map(_.index.name.value)).routes),
      List(InferenceRoute(models).routes),
      List(MetricsRoute(metrics).routes)
    ).flatten.reduce(_ <+> _)
    api <- API.start(routes, config.core.host, config.core.port)
    _   <- Resource.eval(Logo.lines.map(line => info(line)).sequence)
  } yield {
    api
  }
}

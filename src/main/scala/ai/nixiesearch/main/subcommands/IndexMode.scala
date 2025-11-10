package ai.nixiesearch.main.subcommands

import ai.nixiesearch.api.{API, AdminRoute, HealthRoute, IndexModifyRoute, MainRoute, MappingRoute, MetricsRoute}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.{CacheConfig, Config, InferenceConfig}
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.metrics.Metrics
import ai.nixiesearch.core.{Logging, PrintProgress}
import ai.nixiesearch.index.{Indexer, Models}
import ai.nixiesearch.index.sync.Index
import ai.nixiesearch.main.CliConfig.CliArgs.IndexArgs
import ai.nixiesearch.main.CliConfig.IndexSourceArgs.{ApiIndexSourceArgs, FileIndexSourceArgs, KafkaIndexSourceArgs}
import ai.nixiesearch.main.Logo
import ai.nixiesearch.main.subcommands.util.PeriodicEvalStream
import ai.nixiesearch.source.{DocumentSource, FileSource, KafkaSource}
import ai.nixiesearch.util.EnvVars
import ai.nixiesearch.util.analytics.AnalyticsReporter
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream

object IndexMode extends Mode[IndexArgs] {
  override def run(args: IndexArgs, env: EnvVars): IO[Unit] = for {
    _       <- info("Starting in 'index' mode with indexer only ")
    config  <- Config.load(args.config, env)
    indexes <- IO(config.schema.values.toList)
    _       <- AnalyticsReporter
      .create(config, args.mode)
      .use(_ =>
        args.source match {
          case apiConfig: ApiIndexSourceArgs   => runApi(indexes, apiConfig, config)
          case fileConfig: FileIndexSourceArgs =>
            runOffline(
              indexes,
              FileSource(fileConfig),
              fileConfig.index,
              config.core.cache,
              config.inference,
              fileConfig.forceMerge
            )
          case kafkaConfig: KafkaIndexSourceArgs =>
            runOffline(
              indexes,
              KafkaSource(kafkaConfig),
              kafkaConfig.index,
              config.core.cache,
              config.inference,
              None
            )
        }
      )
  } yield {}

  def runOffline(
      indexes: List[IndexMapping],
      source: DocumentSource,
      index: String,
      cacheConfig: CacheConfig,
      inference: InferenceConfig,
      forceMerge: Option[Int]
  ): IO[Unit] = {
    val server = for {
      indexMapping <- Resource.eval(
        IO
          .fromOption(indexes.find(_.name.value == index))(UserError(s"Index '${index}' not found in mapping."))
      )
      _       <- Resource.eval(debug(s"found index mapping for index '${indexMapping.name}'"))
      metrics <- Resource.pure(Metrics())
      models  <- Models.create(inference, cacheConfig, metrics)
      index   <- Index.forIndexing(indexMapping, models)
      indexer <- Indexer.open(index, metrics)
      _       <- PeriodicEvalStream.run(
        every = index.mapping.config.indexer.flush.interval,
        action = indexer.flush().flatMap {
          case true  => index.sync()
          case false => IO.unit
        }
      )
    } yield {
      indexer
    }
    server.use(indexer =>
      for {
        _ <- Logo.lines.map(line => info(line)).sequence
        _ <- source
          .stream(indexer.index.mapping)
          .chunkN(1024)
          .through(PrintProgress.tapChunk("indexed docs"))
          .evalMap(batch => indexer.addDocuments(batch.toList))
          .compile
          .drain
        _ <- indexer.flush()
        _ <- forceMerge match {
          case None           => IO.unit
          case Some(segments) => indexer.merge(segments)
        }
        _ <- indexer.index.sync()
      } yield {
        logger.info(s"Indexing completed successfully.")
      }
    )
  }

  def runApi(indexes: List[IndexMapping], source: ApiIndexSourceArgs, config: Config): IO[Unit] = {
    val server = for {
      _        <- Resource.eval(info("Starting in 'index' mode with only indexer available as a REST API."))
      metrics  <- Resource.pure(Metrics())
      models   <- Models.create(config.inference, config.core.cache, metrics)
      indexers <- indexes
        .map(im =>
          for {
            index   <- Index.forIndexing(im, models)
            indexer <- Indexer.open(index, metrics)
            _       <- PeriodicEvalStream.run(
              every = index.mapping.config.indexer.flush.interval,
              action = indexer.flush().flatMap {
                case true  => index.sync()
                case false => IO.unit
              }
            )
          } yield { indexer }
        )
        .sequence

      routes = List(
        indexers.map(indexer => IndexModifyRoute(indexer).routes <+> MappingRoute(indexer.index).routes),
        List(HealthRoute().routes),
        List(AdminRoute(config).routes),
        List(MainRoute().routes),
        List(MetricsRoute(metrics).routes)
      ).flatten.reduce(_ <+> _)
      api <- API.start(routes, source.host, source.port)
      _   <- Resource.eval(Logo.lines.map(line => info(line)).sequence)
    } yield {
      api
    }
    server.use(_ => IO.never)
  }

}

package ai.nixiesearch.main.subcommands

import ai.nixiesearch.api.{API, AdminRoute, HealthRoute, IndexRoute, MainRoute, MappingRoute}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.{CacheConfig, Config, InferenceConfig}
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.{Logging, PrintProgress}
import ai.nixiesearch.index.{Indexer, Models}
import ai.nixiesearch.index.sync.Index
import ai.nixiesearch.main.CliConfig.CliArgs.IndexArgs
import ai.nixiesearch.main.CliConfig.CliArgs.IndexSourceArgs.{
  ApiIndexSourceArgs,
  FileIndexSourceArgs,
  KafkaIndexSourceArgs
}
import ai.nixiesearch.main.Logo
import ai.nixiesearch.main.subcommands.util.PeriodicFlushStream
import ai.nixiesearch.source.{DocumentSource, FileSource, KafkaSource}
import cats.effect.{IO, Resource}
import cats.implicits.*
import fs2.Stream
import org.http4s.HttpRoutes

object IndexMode extends Logging {
  def run(args: IndexArgs, config: Config): IO[Unit] = for {
    _       <- info("Starting in 'index' mode with indexer only ")
    indexes <- IO(config.schema.values.toList)
    _ <- args.source match {
      case apiConfig: ApiIndexSourceArgs => runApi(indexes, apiConfig, config)
      case fileConfig: FileIndexSourceArgs =>
        runOffline(indexes, FileSource(fileConfig), fileConfig.index, config.core.cache, config.inference)
      case kafkaConfig: KafkaIndexSourceArgs =>
        runOffline(indexes, KafkaSource(kafkaConfig), kafkaConfig.index, config.core.cache, config.inference)
    }
  } yield {}

  def runOffline(
      indexes: List[IndexMapping],
      source: DocumentSource,
      index: String,
      cacheConfig: CacheConfig,
      inference: InferenceConfig
  ): IO[Unit] = {
    val server = for {
      indexMapping <- Resource.eval(
        IO
          .fromOption(indexes.find(_.name.value == index))(UserError(s"index '${index} not found in mapping'"))
      )
      _       <- Resource.eval(debug(s"found index mapping for index '${indexMapping.name}'"))
      models  <- Models.create(inference, cacheConfig)
      index   <- Index.forIndexing(indexMapping, models)
      indexer <- Indexer.open(index)
    } yield {
      indexer
    }
    server.use(indexer =>
      for {
        _ <- Logo.lines.map(line => info(line)).sequence
        _ <- source
          .stream()
          .chunkN(1024)
          .through(PrintProgress.tapChunk("indexed docs"))
          .evalMap(batch => indexer.addDocuments(batch.toList) *> indexer.flush())
          .compile
          .drain
        _ <- indexer.flush()
        _ <- indexer.index.sync()
      } yield {
        logger.info(s"indexing done")
      }
    )
  }

  def runApi(indexes: List[IndexMapping], source: ApiIndexSourceArgs, config: Config): IO[Unit] = {
    val server = for {
      _      <- Resource.eval(info("Starting in 'index' mode with only indexer available as a REST API"))
      models <- Models.create(config.inference, config.core.cache)
      indexers <- indexes
        .map(im =>
          for {
            index   <- Index.forIndexing(im, models)
            indexer <- Indexer.open(index)
            _       <- PeriodicFlushStream.run(index, indexer)
          } yield { indexer }
        )
        .sequence
      routes = List(
        indexers.map(indexer => IndexRoute(indexer).routes <+> MappingRoute(indexer.index).routes),
        List(HealthRoute().routes),
        List(AdminRoute(config).routes),
        List(MainRoute().routes)
      ).flatten.reduce(_ <+> _)
      api <- API.start(routes, source.host, source.port)
      _   <- Resource.eval(Logo.lines.map(line => info(line)).sequence)
    } yield {
      api
    }
    server.use(_ => IO.never)
  }

}

package ai.nixiesearch.main.subcommands

import ai.nixiesearch.api.API.info
import ai.nixiesearch.api.{API, AdminRoute, HealthRoute, IndexRoute, MainRoute, MappingRoute}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.{CacheConfig, Config, InferenceConfig}
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.{Logging, PrintProgress}
import ai.nixiesearch.index.Indexer
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
import cats.effect.IO
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
  ): IO[Unit] = for {
    indexMapping <- IO
      .fromOption(indexes.find(_.name.value == index))(UserError(s"index '${index} not found in mapping'"))
    _ <- debug(s"found index mapping for index '${indexMapping.name}'")
    _ <- Index
      .forIndexing(indexMapping, cacheConfig, inference)
      .use(index =>
        Indexer
          .open(index)
          .use(indexer =>
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
              _ <- index.sync()
            } yield {}
          )
      )
  } yield {
    logger.info(s"indexing done")
  }

  def runApi(indexes: List[IndexMapping], source: ApiIndexSourceArgs, config: Config): IO[Unit] = for {
    _ <- indexes
      .map(im =>
        for {
          index   <- Index.forIndexing(im, config.core.cache, config.inference)
          indexer <- Indexer.open(index)
          _       <- PeriodicFlushStream.run(index, indexer)
        } yield { indexer }
      )
      .sequence
      .use(indexers =>
        for {
          indexRoutes <- IO(
            indexers.map(indexer => IndexRoute(indexer).routes <+> MappingRoute(indexer.index).routes).reduce(_ <+> _)
          )
          healthRoute <- IO(HealthRoute())
          routes <- IO(
            indexRoutes <+> healthRoute.routes <+> AdminRoute(config).routes <+> MainRoute().routes
          )
          server <- API.start(routes, _ => HttpRoutes.empty[IO], source.host, source.port)
          _      <- server.use(_ => IO.never)

        } yield {}
      )
  } yield {}

}

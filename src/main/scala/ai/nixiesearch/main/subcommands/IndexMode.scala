package ai.nixiesearch.main.subcommands

import ai.nixiesearch.api.API.info
import ai.nixiesearch.api.{API, HealthRoute, IndexRoute}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.{CacheConfig, Config, IndexerConfig}
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.{JsonDocumentStream, Logging}
import ai.nixiesearch.index.Indexer
import ai.nixiesearch.index.sync.Index
import ai.nixiesearch.main.CliConfig.CliArgs.IndexArgs
import ai.nixiesearch.main.CliConfig.CliArgs.IndexSource.{ApiIndexSource, FileIndexSource}
import ai.nixiesearch.main.Logo
import ai.nixiesearch.util.source.URLReader
import cats.effect.{IO, Resource}
import cats.implicits.*
import fs2.Stream

import scala.concurrent.duration.*

object IndexMode extends Logging {
  def run(args: IndexArgs): IO[Unit] = for {
    _       <- info("Starting in 'index' mode with indexer only ")
    config  <- Config.load(args.config)
    indexes <- IO(config.schema.values.toList)
    _ <- args.source match {
      case apiConfig: ApiIndexSource   => runApi(indexes, apiConfig)
      case fileConfig: FileIndexSource => runOffline(indexes, fileConfig)
    }
  } yield {}

  def runOffline(indexes: List[IndexMapping], source: FileIndexSource): IO[Unit] = for {
    indexMapping <- IO
      .fromOption(indexes.find(_.name == source.index))(UserError(s"index '${source.index} not found in mapping'"))
    _ <- debug(s"found index mapping for index '${indexMapping.name}'")
    _ <- Index
      .forIndexing(indexMapping)
      .use(index =>
        Indexer
          .open(index)
          .use(indexer =>
            for {
              _ <- Logo.lines.map(line => info(line)).sequence
              _ <- URLReader
                .bytes(source.url, recursive = source.recursive)
                .through(JsonDocumentStream.parse)
                .chunkN(1024)
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

  def runApi(indexes: List[IndexMapping], source: ApiIndexSource): IO[Unit] = for {
    _ <- indexes
      .map(im =>
        for {
          index   <- Index.forIndexing(im)
          indexer <- Indexer.open(index)
          _ <- Stream
            .repeatEval(indexer.flush().flatMap {
              case false => IO.unit
              case true  => index.sync()
            })
            .metered(1.second)
            .compile
            .drain
            .background
        } yield { indexer }
      )
      .sequence
      .use(indexers =>
        for {
          indexRoutes <- IO(indexers.map(indexer => IndexRoute(indexer).routes).reduce(_ <+> _))
          healthRoute <- IO(HealthRoute())
          routes      <- IO(indexRoutes <+> healthRoute.routes)
          server      <- API.start(routes, source.host, source.port)
          _           <- server.use(_ => IO.never)

        } yield {}
      )
  } yield {}

}

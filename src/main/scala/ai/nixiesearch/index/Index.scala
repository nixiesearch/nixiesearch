package ai.nixiesearch.index

import ai.nixiesearch.config.FieldSchema.TextLikeFieldSchema
import ai.nixiesearch.config.StoreConfig.{LocalFileConfig, LocalStoreConfig, MemoryStoreConfig, S3StoreConfig}
import ai.nixiesearch.config.{CacheConfig, StoreConfig}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.SemanticSearchLikeType
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.store.{AsyncDirectory, S3AsyncDirectory}
import cats.effect.IO
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig}
import org.apache.lucene.store.{ByteBuffersDirectory, Directory, MMapDirectory, NIOFSDirectory}
import fs2.Stream

import java.nio.file.{Files, Paths}

case class Index(mapping: IndexMapping, dir: AsyncDirectory, encoders: BiEncoderCache) extends Logging {
  def name = mapping.name

  def close(): IO[Unit] = for {
    _ <- info(s"closing index directory '$dir'")
    _ <- dir.closeAsync()
    _ <- Stream.emits(encoders.encoders.values.toList).evalMap(enc => IO(enc.close())).compile.drain
  } yield {}
}

object Index extends Logging {
  case class InconsistentIndexException(index: String) extends Exception

  def openOrCreate(mapping: IndexMapping, store: StoreConfig, cache: CacheConfig): IO[Index] = for {
    dir <- store match {
      case MemoryStoreConfig() => IO(AsyncDirectory.wrap(new ByteBuffersDirectory()))
      case f: LocalFileConfig =>
        for {
          unsafePath <- IO {
            f match {
              case S3StoreConfig(url, workdir) => workdir
              case LocalStoreConfig(url)       => url.path
            }
          }
          safePath <- IO(Files.exists(unsafePath)).flatMap {
            case true =>
              IO(Files.isDirectory(unsafePath)).flatMap {
                case true  => IO.pure(unsafePath)
                case false => IO.raiseError(InconsistentIndexException(mapping.name))
              }
            case false =>
              IO(Files.createDirectories(unsafePath)) *> info(s"created on-disk dir '$unsafePath'") *> IO.pure(
                unsafePath
              )
          }
          fileDirectory <- f match {
            case S3StoreConfig(url, _) => S3AsyncDirectory.init(url, safePath)
            case LocalStoreConfig(_)   => IO(AsyncDirectory.wrap(new MMapDirectory(safePath)))
          }
        } yield {
          fileDirectory
        }
    }
    _ <- IO(DirectoryReader.indexExists(dir)).flatMap {
      case true => info(s"Index '${mapping.name}' exists in directory ${dir}")
      case false =>
        for {
          _        <- info(s"Index '${mapping.name}' does not exist in directory ${dir}")
          config   <- IO(new IndexWriterConfig(IndexMapping.createAnalyzer(mapping)))
          writer   <- IO(new IndexWriter(dir, config))
          _        <- IO(writer.commit())
          _        <- info("created empty segment")
          manifest <- IndexManifest.create(dir, mapping, 0L)
          _        <- IndexManifest.write(dir, manifest)
          _        <- IO(writer.close())
        } yield {}
    }
    models <- IO(mapping.fields.values.collect {
      case TextLikeFieldSchema(_, SemanticSearchLikeType(model, _), _, _, _, _) => model
    })
    encoders <- BiEncoderCache.create(models.toList, cache.embedding)
    _        <- info(s"Opened index '${mapping.name}'")
  } yield {
    Index(mapping, dir, encoders)
  }
}

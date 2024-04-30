package ai.nixiesearch.index

import ai.nixiesearch.config.FieldSchema.TextLikeFieldSchema
import ai.nixiesearch.config.StoreConfig.{LocalFileConfig, LocalStoreConfig, MemoryStoreConfig, S3StoreConfig}
import ai.nixiesearch.config.{CacheConfig, StoreConfig}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.SemanticSearchLikeType
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.store.RemoteSyncDirectory
import cats.effect.IO
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig}
import org.apache.lucene.store.{ByteBuffersDirectory, Directory, MMapDirectory, NIOFSDirectory}
import fs2.Stream

import java.nio.file.{Files, Paths}

case class Index(mapping: IndexMapping, dir: Directory, encoders: BiEncoderCache) extends Logging {
  def name = mapping.name

  def close(): IO[Unit] = for {
    _ <- info(s"closing index directory '$dir'")
    _ <- IO(dir.close())
    _ <- Stream.emits(encoders.encoders.values.toList).evalMap(enc => IO(enc.close())).compile.drain
  } yield {}
}

object Index extends Logging {
  val INDEXES_DIR_NAME = "indexes"
  case class InconsistentIndexException(index: String) extends Exception

  def openOrCreate(mapping: IndexMapping, store: StoreConfig, cache: CacheConfig): IO[Index] = for {
    dir <- openDirectory(store, mapping.name)
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

  private def openDirectory(store: StoreConfig, indexName: String): IO[Directory] = store match {
    case MemoryStoreConfig() => IO(new ByteBuffersDirectory())
    case f: LocalFileConfig =>
      for {
        unsafePath <- IO {
          f match {
            case S3StoreConfig(url, workdir) => workdir.resolve(INDEXES_DIR_NAME).resolve(indexName)
            case LocalStoreConfig(url)       => url.path.resolve(INDEXES_DIR_NAME).resolve(indexName)
          }
        }
        safePath <- IO(Files.exists(unsafePath)).flatMap {
          case true =>
            IO(Files.isDirectory(unsafePath)).flatMap {
              case true  => IO.pure(unsafePath)
              case false => IO.raiseError(InconsistentIndexException(indexName))
            }
          case false =>
            IO(Files.createDirectories(unsafePath)) *> info(s"created on-disk dir '$unsafePath'") *> IO.pure(
              unsafePath
            )
        }
        fileDirectory <- f match {
          case S3StoreConfig(url, _) => RemoteSyncDirectory.init(url, safePath)
          case LocalStoreConfig(_)   => IO(new MMapDirectory(safePath))
        }
      } yield {
        fileDirectory
      }
  }
}

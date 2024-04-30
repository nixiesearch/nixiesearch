package ai.nixiesearch.index

import ai.nixiesearch.config.FieldSchema.TextLikeFieldSchema
import ai.nixiesearch.config.{CacheConfig, StoreConfig}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.SemanticSearchLikeType
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.store.NixieDirectory
import cats.effect.IO
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig}
import org.apache.lucene.store.{ByteBuffersDirectory, MMapDirectory, NIOFSDirectory}

import java.nio.file.Paths

case class Index(mapping: IndexMapping, dir: NixieDirectory, encoders: BiEncoderCache) {
  def name = mapping.name
}

object Index extends Logging {
  def openOrCreate(mapping: IndexMapping, store: StoreConfig, cache: CacheConfig): IO[Index] = for {
    luceneDir <- store match {
      case StoreConfig.S3StoreConfig(url, workdir) => IO.raiseError(new UnsupportedOperationException())
      case StoreConfig.LocalStoreConfig(url)       => IO(new MMapDirectory(Paths.get(url.path)))
      case StoreConfig.MemoryStoreConfig()         => IO(new ByteBuffersDirectory())
    }
    dir <- IO(NixieDirectory(luceneDir))
    _ <- IO(DirectoryReader.indexExists(dir)).flatMap {
      case true => info(s"Index '${mapping.name}' exists in directory ${luceneDir}")
      case false =>
        for {
          _      <- info(s"Index '${mapping.name}' does not exist in directory ${luceneDir}")
          config <- IO(new IndexWriterConfig(IndexMapping.createAnalyzer(mapping)))
          writer <- IO(new IndexWriter(dir, config))
          _      <- IO(writer.commit())
          _      <- info("created empty segment")
          _      <- IO(writer.close())
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

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
import org.apache.lucene.store.ByteBuffersDirectory

case class Index(mapping: IndexMapping, dir: NixieDirectory, encoders: BiEncoderCache) {
  def name = mapping.name
}

object Index extends Logging {
  def openOrCreate(mapping: IndexMapping, store: StoreConfig, cache: CacheConfig): IO[Index] = for {
    luceneDir <- store match {
      case StoreConfig.S3StoreConfig(url, workdir) => IO.raiseError(new UnsupportedOperationException())
      case StoreConfig.LocalStoreConfig(url)       => IO.raiseError(new UnsupportedOperationException())
      case StoreConfig.MemoryStoreConfig()         => IO(new ByteBuffersDirectory())
    }
    dir <- IO(NixieDirectory(luceneDir))
    models <- IO(mapping.fields.values.collect {
      case TextLikeFieldSchema(_, SemanticSearchLikeType(model, _), _, _, _, _) => model
    })
    encoders <- BiEncoderCache.create(models.toList, cache.embedding)
    _        <- info(s"Detected index ${mapping.name}")
  } yield {
    Index(mapping, dir, encoders)
  }
}

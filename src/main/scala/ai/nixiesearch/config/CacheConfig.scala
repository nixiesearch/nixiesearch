package ai.nixiesearch.config

import ai.nixiesearch.config.CacheConfig.EmbeddingCacheConfig
import io.circe.Decoder

case class CacheConfig(embedding: EmbeddingCacheConfig = EmbeddingCacheConfig())

object CacheConfig {
  case class EmbeddingCacheConfig(maxSize: Int = 32 * 1024)

  given cacheConfigDecoder: Decoder[CacheConfig] = Decoder.instance(c =>
    for {
      emb <- c.downField("embedding").as[Option[EmbeddingCacheConfig]]
    } yield {
      CacheConfig(emb.getOrElse(EmbeddingCacheConfig()))
    }
  )

  given embCacheConfigDecoder: Decoder[EmbeddingCacheConfig] = Decoder.instance(c =>
    for {
      maxSize <- c.downField("maxSize").as[Option[Int]]
    } yield {
      EmbeddingCacheConfig(maxSize.getOrElse(EmbeddingCacheConfig().maxSize))
    }
  )
}

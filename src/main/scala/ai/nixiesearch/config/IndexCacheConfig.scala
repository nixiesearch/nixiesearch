package ai.nixiesearch.config

import ai.nixiesearch.config.IndexCacheConfig.EmbeddingCacheConfig
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

case class IndexCacheConfig(embedding: EmbeddingCacheConfig = EmbeddingCacheConfig())

object IndexCacheConfig {
  case class EmbeddingCacheConfig(maxSize: Int = 32 * 1024)

  given cacheConfigEncoder: Encoder[IndexCacheConfig] = deriveEncoder
  given cacheConfigDecoder: Decoder[IndexCacheConfig] = Decoder.instance(c =>
    for {
      emb <- c.downField("embedding").as[Option[EmbeddingCacheConfig]]
    } yield {
      IndexCacheConfig(emb.getOrElse(EmbeddingCacheConfig()))
    }
  )

  given embCacheConfigEncoder: Encoder[EmbeddingCacheConfig] = deriveEncoder
  given embCacheConfigDecoder: Decoder[EmbeddingCacheConfig] = Decoder.instance(c =>
    for {
      maxSize <- c.downField("maxSize").as[Option[Int]]
    } yield {
      EmbeddingCacheConfig(maxSize.getOrElse(EmbeddingCacheConfig().maxSize))
    }
  )
}

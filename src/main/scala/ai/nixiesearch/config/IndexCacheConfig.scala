package ai.nixiesearch.config

import ai.nixiesearch.config.IndexCacheConfig.EmbeddingCacheConfig
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

case class IndexCacheConfig()

object IndexCacheConfig {
  case class EmbeddingCacheConfig(maxSize: Int = 32 * 1024)

  given cacheConfigEncoder: Encoder[IndexCacheConfig] = deriveEncoder
  given cacheConfigDecoder: Decoder[IndexCacheConfig] = Decoder.const(IndexCacheConfig())

}

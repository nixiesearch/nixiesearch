package ai.nixiesearch.config

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

case class CoreConfig(cache: CacheConfig = CacheConfig())

object CoreConfig {
  given coreConfigEncoder: Encoder[CoreConfig] = deriveEncoder
  given coreConfigDecoder: Decoder[CoreConfig] = Decoder.instance(c =>
    for {
      cache <- c.downField("cache").as[Option[CacheConfig]]
    } yield {
      CoreConfig(cache = cache.getOrElse(CacheConfig()))
    }
  )
}

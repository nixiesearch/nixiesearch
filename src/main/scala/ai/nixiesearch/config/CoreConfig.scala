package ai.nixiesearch.config

import io.circe.Decoder

case class CoreConfig(cache: CacheConfig = CacheConfig())

object CoreConfig {
  given coreConfigDecoder: Decoder[CoreConfig] = Decoder.instance(c =>
    for {
      cache <- c.downField("cache").as[Option[CacheConfig]]
    } yield {
      CoreConfig(cache.getOrElse(CacheConfig()))
    }
  )
}

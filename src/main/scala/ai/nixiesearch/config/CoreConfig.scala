package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.{Hostname, Port}
import ai.nixiesearch.config.CoreConfig.{DEFAULT_HOST, DEFAULT_PORT}
import ai.nixiesearch.main.CliConfig.Loglevel
import ai.nixiesearch.main.CliConfig.Loglevel.INFO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

case class CoreConfig(
    cache: CacheConfig = CacheConfig(),
    host: Hostname = DEFAULT_HOST,
    port: Port = DEFAULT_PORT,
    loglevel: Loglevel = INFO
)

object CoreConfig {
  val DEFAULT_HOST = Hostname("0.0.0.0")
  val DEFAULT_PORT = Port(8080)

  given coreConfigEncoder: Encoder[CoreConfig] = deriveEncoder
  given coreConfigDecoder: Decoder[CoreConfig] = Decoder.instance(c =>
    for {
      cache    <- c.downField("cache").as[Option[CacheConfig]]
      host     <- c.downField("host").as[Option[Hostname]]
      port     <- c.downField("port").as[Option[Port]]
      loglevel <- c.downField("loglevel").as[Option[Loglevel]]
    } yield {
      CoreConfig(
        cache = cache.getOrElse(CacheConfig()),
        host = host.getOrElse(DEFAULT_HOST),
        port = port.getOrElse(DEFAULT_PORT),
        loglevel = loglevel.getOrElse(INFO)
      )
    }
  )
}

package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.{Hostname, Port}
import ai.nixiesearch.config.CoreConfig.{DEFAULT_HOST, DEFAULT_PORT, TelemetryConfig}
import ai.nixiesearch.main.CliConfig.Loglevel
import ai.nixiesearch.main.CliConfig.Loglevel.INFO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

case class CoreConfig(
    cache: CacheConfig = CacheConfig(),
    host: Hostname = DEFAULT_HOST,
    port: Port = DEFAULT_PORT,
    loglevel: Loglevel = INFO,
    telemetry: TelemetryConfig = TelemetryConfig()
)

object CoreConfig {
  val DEFAULT_HOST = Hostname("0.0.0.0")
  val DEFAULT_PORT = Port(8080)

  case class TelemetryConfig(usage: Boolean = true)
  given telemetryConfigEncoder: Encoder[TelemetryConfig] = deriveEncoder
  given telemetryConfigDecoder: Decoder[TelemetryConfig] = Decoder.instance(c =>
    c.as[Boolean] match {
      case Left(_) =>
        for {
          usage <- c.downField("usage").as[Option[Boolean]]
        } yield {
          TelemetryConfig(usage = usage.getOrElse(true))
        }
      case Right(enabled) => Right(TelemetryConfig(usage = enabled))
    }
  )

  given coreConfigEncoder: Encoder[CoreConfig] = deriveEncoder
  given coreConfigDecoder: Decoder[CoreConfig] = Decoder.instance(c =>
    for {
      cache     <- c.downField("cache").as[Option[CacheConfig]]
      host      <- c.downField("host").as[Option[Hostname]]
      port      <- c.downField("port").as[Option[Port]]
      loglevel  <- c.downField("loglevel").as[Option[Loglevel]]
      telemetry <- c.downField("telemetry").as[Option[TelemetryConfig]]
    } yield {
      CoreConfig(
        cache = cache.getOrElse(CacheConfig()),
        host = host.getOrElse(DEFAULT_HOST),
        port = port.getOrElse(DEFAULT_PORT),
        loglevel = loglevel.getOrElse(INFO),
        telemetry = telemetry.getOrElse(TelemetryConfig())
      )
    }
  )
}

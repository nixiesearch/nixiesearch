package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.mapping.IndexConfig.{FlushConfig, MappingConfig}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

import scala.concurrent.duration.*
import scala.concurrent.duration.FiniteDuration

case class IndexConfig(mapping: MappingConfig = MappingConfig(), flush: FlushConfig = FlushConfig())

object IndexConfig {
  import DurationJson.given

  case class MappingConfig(dynamic: Boolean = false)
  case class FlushConfig(interval: FiniteDuration = 5.seconds)

  given mappingConfigEncoder: Encoder[MappingConfig] = deriveEncoder
  given mappingConfigDecoder: Decoder[MappingConfig] = Decoder.instance(c =>
    for {
      dynamic <- c.downField("dynamic").as[Option[Boolean]].map(_.getOrElse(false))
    } yield {
      MappingConfig(dynamic)
    }
  )

  given flushConfigEncoder: Encoder[FlushConfig] = deriveEncoder
  given flushConfigDecoder: Decoder[FlushConfig] = Decoder.instance(c =>
    for {
      interval <- c.downField("interval").as[Option[FiniteDuration]]
    } yield {
      FlushConfig(interval.getOrElse(5.seconds))
    }
  )

  given indexConfigDecoder: Decoder[IndexConfig] = Decoder.instance(c =>
    for {
      mapping <- c.downField("mapping").as[Option[MappingConfig]].map(_.getOrElse(MappingConfig()))
      flush   <- c.downField("flush").as[Option[FlushConfig]].map(_.getOrElse(FlushConfig()))
    } yield {
      IndexConfig(mapping, flush)
    }
  )

  given indexConfigEncoder: Encoder[IndexConfig] = deriveEncoder
}

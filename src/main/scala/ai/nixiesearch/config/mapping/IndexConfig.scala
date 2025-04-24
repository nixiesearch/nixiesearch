package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.mapping.IndexConfig.FlushConfig
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

import scala.concurrent.duration.*
import scala.concurrent.duration.FiniteDuration

case class IndexConfig(
    flush: FlushConfig = FlushConfig()
)

object IndexConfig {
  import DurationJson.given

  case class FlushConfig(interval: FiniteDuration = 5.seconds)
  case class HnswConfig(m: Int = 16, efc: Int = 100, workers: Int = Runtime.getRuntime.availableProcessors())


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
      flush   <- c.downField("flush").as[Option[FlushConfig]].map(_.getOrElse(FlushConfig()))
    } yield {
      IndexConfig(flush)
    }
  )

  given indexConfigEncoder: Encoder[IndexConfig] = deriveEncoder
}

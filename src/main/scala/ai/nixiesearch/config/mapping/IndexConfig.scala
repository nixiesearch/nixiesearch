package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.mapping.IndexConfig.{FlushConfig, HnswConfig, MappingConfig}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

import scala.concurrent.duration.*
import scala.concurrent.duration.FiniteDuration

case class IndexConfig(
    mapping: MappingConfig = MappingConfig(),
    flush: FlushConfig = FlushConfig(),
    hnsw: HnswConfig = HnswConfig()
)

object IndexConfig {
  import DurationJson.given

  case class MappingConfig(dynamic: Boolean = false)
  case class FlushConfig(interval: FiniteDuration = 5.seconds)
  case class HnswConfig(m: Int = 16, efc: Int = 100, workers: Int = Runtime.getRuntime.availableProcessors())

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

  given hnswConfigEncoder: Encoder[HnswConfig] = deriveEncoder
  given hnswConfigDecoder: Decoder[HnswConfig] = Decoder.instance(c =>
    for {
      m       <- c.downField("m").as[Option[Int]]
      efc     <- c.downField("efc").as[Option[Int]]
      workers <- c.downField("workers").as[Option[Int]]
    } yield {
      HnswConfig(m.getOrElse(16), efc.getOrElse(100), workers.getOrElse(Runtime.getRuntime.availableProcessors()))
    }
  )

  given indexConfigDecoder: Decoder[IndexConfig] = Decoder.instance(c =>
    for {
      mapping <- c.downField("mapping").as[Option[MappingConfig]].map(_.getOrElse(MappingConfig()))
      flush   <- c.downField("flush").as[Option[FlushConfig]].map(_.getOrElse(FlushConfig()))
      hnsw    <- c.downField("hnsw").as[Option[HnswConfig]].map(_.getOrElse(HnswConfig()))
    } yield {
      IndexConfig(mapping, flush, hnsw)
    }
  )

  given indexConfigEncoder: Encoder[IndexConfig] = deriveEncoder
}

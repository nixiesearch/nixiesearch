package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.mapping.IndexConfig.{FlushConfig, IndexerConfig}
import ai.nixiesearch.util.Size
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

import scala.concurrent.duration.*
import scala.concurrent.duration.FiniteDuration

case class IndexConfig(
    indexer: IndexerConfig = IndexerConfig()
)

object IndexConfig {
  import DurationJson.given

  case class FlushConfig(interval: FiniteDuration = 5.seconds)

  given flushConfigEncoder: Encoder[FlushConfig] = deriveEncoder
  given flushConfigDecoder: Decoder[FlushConfig] = Decoder.instance(c =>
    for {
      interval <- c.downField("interval").as[Option[FiniteDuration]]
    } yield {
      FlushConfig(interval.getOrElse(5.seconds))
    }
  )

  case class IndexerConfig(flush: FlushConfig = FlushConfig(), ramBufferSize: Size = Size.mb(512))
  given indexerConfigDecoder: Decoder[IndexerConfig] = Decoder.instance(c =>
    for {
      flush         <- c.downField("flush").as[Option[FlushConfig]].map(_.getOrElse(FlushConfig()))
      ramBufferSize <- c.downField("ramBufferSize").as[Option[Size]].map(_.getOrElse(Size.mb(512)))
    } yield {
      IndexerConfig(flush, ramBufferSize)
    }
  )
  given indexerConfigEncoder: Encoder[IndexerConfig] = deriveEncoder

  given indexConfigDecoder: Decoder[IndexConfig] = Decoder.instance(c =>
    for {
      indexer <- c.downField("indexer").as[Option[IndexerConfig]].map(_.getOrElse(IndexerConfig()))
    } yield {
      IndexConfig(indexer)
    }
  )

  given indexConfigEncoder: Encoder[IndexConfig] = deriveEncoder
}

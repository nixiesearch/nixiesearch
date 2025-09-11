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

  case class IndexerConfig(
      flush: FlushConfig = FlushConfig(),
      ram_buffer_size: Size = Size.mb(512),
      merge_policy: Option[MergePolicyConfig] = None
  )
  given indexerConfigDecoder: Decoder[IndexerConfig] = Decoder.instance(c =>
    for {
      flush          <- c.downField("flush").as[Option[FlushConfig]].map(_.getOrElse(FlushConfig()))
      ramBufferSize1 <- c.downField("ramBufferSize").as[Option[Size]]
      ramBufferSize2 <- c.downField("ram_buffer_size").as[Option[Size]]
      merge_policy   <- c.downField("merge_policy").as[Option[MergePolicyConfig]]
    } yield {
      val size = (ramBufferSize1, ramBufferSize2) match {
        case (Some(a), Some(b)) => a
        case (Some(a), None)    => a
        case (None, Some(b))    => b
        case (None, None)       => Size.mb(512)
      }
      IndexerConfig(flush, size, merge_policy)
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

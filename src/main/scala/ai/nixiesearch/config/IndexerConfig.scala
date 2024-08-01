package ai.nixiesearch.config

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

case class IndexerConfig()

object IndexerConfig {
  given indexerConfigEncoder: Encoder[IndexerConfig] = deriveEncoder
  given indexerConfigDecoder: Decoder[IndexerConfig] = Decoder.const(IndexerConfig())
}

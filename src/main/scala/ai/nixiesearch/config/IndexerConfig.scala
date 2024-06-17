package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.{Hostname, Port}
import io.circe.{Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.*

case class IndexerConfig()

object IndexerConfig {
  given indexerConfigEncoder: Encoder[IndexerConfig] = deriveEncoder
  given indexerConfigDecoder: Decoder[IndexerConfig] = Decoder.const(IndexerConfig())
}

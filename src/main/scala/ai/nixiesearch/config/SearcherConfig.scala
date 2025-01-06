package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.{Hostname, Port}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

case class SearcherConfig()

object SearcherConfig {
  given searcherConfigEncoder: Encoder[SearcherConfig] = deriveEncoder
  given searcherConfigDecoder: Decoder[SearcherConfig] = Decoder.const(SearcherConfig())
}

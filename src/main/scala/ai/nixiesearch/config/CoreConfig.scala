package ai.nixiesearch.config

import io.circe.Decoder

case class CoreConfig()

object CoreConfig {
  given coreConfigDecoder: Decoder[CoreConfig] = Decoder.const(CoreConfig())
}

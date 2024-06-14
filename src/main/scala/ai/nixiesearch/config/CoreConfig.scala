package ai.nixiesearch.config

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

case class CoreConfig()

object CoreConfig {
  given coreConfigEncoder: Encoder[CoreConfig] = deriveEncoder
  given coreConfigDecoder: Decoder[CoreConfig] = Decoder.const(CoreConfig())
}

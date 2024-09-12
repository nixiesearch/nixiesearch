package ai.nixiesearch.core.nn

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

case class ModelRef(name: String)

object ModelRef {
  given modelRefEncoder: Encoder[ModelRef] = Encoder.encodeString.contramap(_.name)
  given modelRefDecoder: Decoder[ModelRef] = Decoder.decodeString.map(ModelRef.apply)

  given modelRefKeyDecoder: KeyDecoder[ModelRef] = KeyDecoder.decodeKeyString.map(ModelRef.apply)
  given modelRefKeyEncoder: KeyEncoder[ModelRef] = KeyEncoder.encodeKeyString.contramap(_.name)
}

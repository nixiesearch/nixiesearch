package ai.nixiesearch.util

import io.circe.{Decoder, Encoder}

import java.nio.file.{Path, Paths}

object PathJson {
  given pathDecoder: Decoder[Path] = Decoder.decodeString.map(s => Paths.get(s))
  given pathEncoder: Encoder[Path] = Encoder.encodeString.contramap(_.toString)

}

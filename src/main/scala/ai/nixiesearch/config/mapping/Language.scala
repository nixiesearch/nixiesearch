package ai.nixiesearch.config

import scala.util.{Failure, Success}
import io.circe.{Decoder, Encoder}
import io.circe.Json

sealed trait Language

object Language {
  case object Generic extends Language
  case object English extends Language

  implicit val languageTypeDecoder: Decoder[Language] = Decoder.decodeString.emapTry {
    case "en" | "english" => Success(English)
    case "generic"        => Success(Generic)
    case other            => Failure(new Exception(s"language '$other' is not supported. maybe try 'english'?"))
  }

  implicit val languageTypeEncoder: Encoder[Language] = Encoder.instance {
    case English => Json.fromString("english")
    case Generic => Json.fromString("generic")
  }
}

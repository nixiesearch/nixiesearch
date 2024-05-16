package ai.nixiesearch.config

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import org.http4s.Uri

import java.nio.file.{Files, Path, Paths}
import scala.util.{Failure, Success}

enum URL {
  case LocalURL(path: Path)                  extends URL
  case HttpURL(path: Uri)                    extends URL
  case S3URL(bucket: String, prefix: String) extends URL
}

object URL {
  given urlEncoder: Encoder[URL] = Encoder.instance {
    case URL.LocalURL(path)        => Json.fromString(s"file://$path")
    case URL.HttpURL(path)         => Json.fromString(path.renderString)
    case URL.S3URL(bucket, prefix) => Json.fromString(s"s3://$bucket/$prefix")
  }

  val localScheme3Pattern = "file:///(.*)".r
  val localScheme2Pattern = "file://(.*)".r
  val localPattern        = "/(.*)".r
  val s3Pattern           = "s3://([a-zA-Z0-9\\-\\.]{3,})/(.+)".r
  val httpPattern         = "(https?://.*)".r
  val catchAllPrefix      = "([a-zA-Z0-9]+://.*)".r
  given urlDecoder: Decoder[URL] = Decoder.decodeString.emapTry {
    case localScheme3Pattern(path) => Success(LocalURL(Paths.get("/" + path)))
    case localScheme2Pattern(path) => Success(LocalURL(Paths.get("/" + path)))
    case localPattern(path)        => Success(LocalURL(Paths.get("/" + path)))
    case s3Pattern(bucket, prefix) => Success(URL.S3URL(bucket, prefix))
    case httpPattern(http) =>
      Uri.fromString(http) match {
        case Left(value)  => Failure(DecodingFailure(s"cannot decode HTTP uri '$http': ${value}", Nil))
        case Right(value) => Success(HttpURL(value))
      }
    case catchAllPrefix(url) => Failure(DecodingFailure(s"cannot decode URL '$url'", Nil))
    case relative            => Success(LocalURL(Paths.get(System.getProperty("user.dir"), relative)))
  }
}

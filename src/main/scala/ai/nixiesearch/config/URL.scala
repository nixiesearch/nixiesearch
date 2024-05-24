package ai.nixiesearch.config

import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import org.http4s.Uri
import io.circe.generic.semiauto.*
import java.nio.file.{Files, Path, Paths}
import scala.util.{Failure, Success}

enum URL {
  case LocalURL(path: Path)                                                                                  extends URL
  case HttpURL(path: Uri)                                                                                    extends URL
  case S3URL(bucket: String, prefix: String, region: Option[String] = None, endpoint: Option[String] = None) extends URL
}

object URL {
  given s3Encoder: Encoder[S3URL] = deriveEncoder
  given s3Decoder: Decoder[S3URL] = deriveDecoder

  given urlEncoder: Encoder[URL] = Encoder.instance {
    case URL.LocalURL(path)                    => Json.fromString(s"file://$path")
    case URL.HttpURL(path)                     => Json.fromString(path.renderString)
    case URL.S3URL(bucket, prefix, None, None) => Json.fromString(s"s3://$bucket/$prefix")
    case u @ URL.S3URL(bucket, prefix, _, _)   => Json.fromJsonObject(JsonObject.fromMap(Map("s3" -> s3Encoder(u))))
  }

  val localScheme3Pattern = "file:///(.*)".r
  val localScheme2Pattern = "file://(.*)".r
  val localScheme1Pattern = "file:/(.*)".r
  val localPattern        = "/(.*)".r
  val s3Pattern           = "s3://([a-zA-Z0-9\\-\\.]{3,})/(.+)".r
  val httpPattern         = "(https?://.*)".r
  val catchAllPrefix      = "([a-zA-Z0-9]+://.*)".r
  given urlDecoder: Decoder[URL] = Decoder.instance(c =>
    c.focus match {
      case Some(json) =>
        json.asString match {
          case Some(string) => fromString(string)
          case None =>
            json.asObject match {
              case Some(obj) =>
                obj.toMap.get("s3") match {
                  case Some(value) => s3Decoder.decodeJson(value)
                  case None        => Left(DecodingFailure("cannot decode url", c.history))
                }
              case None => Left(DecodingFailure("cannot decode url", c.history))
            }
        }
      case None => Left(DecodingFailure(s"cannot decode url", c.history))
    }
  )

  def fromString(string: String): Either[DecodingFailure, URL] = string match {
    case localScheme3Pattern(path) => Right(LocalURL(Paths.get("/" + path)))
    case localScheme2Pattern(path) => Right(LocalURL(Paths.get("/" + path)))
    case localScheme1Pattern(path) => Right(LocalURL(Paths.get("/" + path)))
    case localPattern(path)        => Right(LocalURL(Paths.get("/" + path)))
    case s3Pattern(bucket, prefix) => Right(URL.S3URL(bucket, prefix, None, None))
    case httpPattern(http) =>
      Uri.fromString(http) match {
        case Left(value)  => Left(DecodingFailure(s"cannot decode HTTP uri '$http': ${value}", Nil))
        case Right(value) => Right(HttpURL(value))
      }
    case catchAllPrefix(url) => Left(DecodingFailure(s"cannot decode URL '$url'", Nil))
    case relative            => Right(LocalURL(Paths.get(System.getProperty("user.dir"), relative)))

  }
}

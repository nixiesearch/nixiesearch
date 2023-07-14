package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.Hostname
import ai.nixiesearch.config.StoreConfig.StoreUrl
import ai.nixiesearch.config.StoreConfig.StoreUrl.LocalStoreUrl
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.nio.file.Path
import scala.util.{Failure, Success}

case class StoreConfig(remote: StoreUrl = LocalStoreUrl(), local: LocalStoreUrl = LocalStoreUrl())

object StoreConfig {
  implicit val storeConfigDecoder: Decoder[StoreConfig] = deriveDecoder

  sealed trait StoreUrl

  object StoreUrl {
    case class S3StoreUrl(bucket: String, prefix: String)           extends StoreUrl
    case class LocalStoreUrl(path: String = "/var/lib/nixiesearch") extends StoreUrl

    implicit val localStoreUrlDecoder: Decoder[LocalStoreUrl] = Decoder.decodeString.emapTry {
      case fileFormat(path) => Success(LocalStoreUrl(path))
      case other            => Failure(new Exception(s"cannot parse file:// URL for format: '$other'"))
    }

    val s3formatNoPrefix = "s3://([a-z0-9\\.\\-]+)/?".r
    val s3formatPrefix   = "s3://([a-z0-9\\.\\-]+)/([a-zA-Z0-9/!\\-\\._\\*\\(\\)]+)".r
    val fileFormat       = "file://?(/.*)".r
    val slashFormat      = "(/.*)".r

    implicit val storeUrlDecoder: Decoder[StoreUrl] = Decoder.decodeString.emapTry {
      case fileFormat(path)               => Success(LocalStoreUrl(path))
      case slashFormat(path)              => Success(LocalStoreUrl(path))
      case s3formatNoPrefix(bucket)       => Success(S3StoreUrl(bucket, "nixiesearch"))
      case s3formatPrefix(bucket, prefix) => Success(S3StoreUrl(bucket, prefix))
      case other                          => Failure(new Exception(s"cannot parse store url '$other'"))
    }
  }
}

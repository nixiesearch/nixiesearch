package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.Hostname
import ai.nixiesearch.config.StoreConfig.StoreUrl
import ai.nixiesearch.config.StoreConfig.StoreUrl.{LocalStoreUrl, S3StoreUrl}
import io.circe.{Decoder, DecodingFailure}
import io.circe.generic.semiauto.deriveDecoder

import java.nio.file.{Path, Paths}
import scala.util.{Failure, Success}

sealed trait StoreConfig {
  def url: StoreUrl
}
object StoreConfig {
  val DEFAULT_WORKDIR = "/var/lib/nixiesearch"

  case class S3StoreConfig(url: S3StoreUrl, workdir: Path)                         extends StoreConfig
  case class LocalStoreConfig(url: LocalStoreUrl = LocalStoreUrl(DEFAULT_WORKDIR)) extends StoreConfig

  implicit val pathDecoder: Decoder[Path] = Decoder.decodeString.map(str => Paths.get(str))
  implicit val storeConfigDecoder: Decoder[StoreConfig] = Decoder.instance(c =>
    c.downField("url").as[StoreUrl](StoreUrl.storeUrlDecoder) match {
      case Left(error) => Left(error)
      case Right(s3: S3StoreUrl) =>
        for {
          workdir <- c.downField("workdir").as[Option[Path]]
        } yield {
          S3StoreConfig(s3, workdir.getOrElse(Paths.get(DEFAULT_WORKDIR)))
        }
      case Right(url: LocalStoreUrl) => Right(LocalStoreConfig(url))
    }
  )
  sealed trait StoreUrl

  object StoreUrl {
    case class S3StoreUrl(bucket: String, prefix: String)    extends StoreUrl
    case class LocalStoreUrl(path: String = DEFAULT_WORKDIR) extends StoreUrl

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

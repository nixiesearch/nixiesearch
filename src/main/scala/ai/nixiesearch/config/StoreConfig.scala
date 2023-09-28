package ai.nixiesearch.config

import ai.nixiesearch.config.ApiConfig.Hostname
import ai.nixiesearch.config.StoreConfig.StoreUrl
import ai.nixiesearch.config.StoreConfig.StoreUrl.{LocalStoreUrl, MemoryUrl, S3StoreUrl, TmpUrl}
import io.circe.{Decoder, DecodingFailure}
import io.circe.generic.semiauto.deriveDecoder

import java.io.File
import java.nio.file.{Files, Path, Paths}
import scala.util.{Failure, Success}

sealed trait StoreConfig {
  def url: StoreUrl
}
object StoreConfig {
  val DEFAULT_WORKDIR = System.getProperty("user.dir")

  case class S3StoreConfig(url: S3StoreUrl, workdir: Path)                         extends StoreConfig
  case class LocalStoreConfig(url: LocalStoreUrl = LocalStoreUrl(DEFAULT_WORKDIR)) extends StoreConfig
  case class MemoryStoreConfig() extends StoreConfig {
    val url: MemoryUrl = MemoryUrl()
  }

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
      case Right(url: TmpUrl) => Right(LocalStoreConfig(LocalStoreUrl(Files.createTempDirectory(url.prefix).toString)))
      case Right(url: LocalStoreUrl) => Right(LocalStoreConfig(url))
      case Right(url: MemoryUrl)     => Right(MemoryStoreConfig())
    }
  )
  sealed trait StoreUrl

  object StoreUrl {
    case class S3StoreUrl(bucket: String, prefix: String)    extends StoreUrl
    case class LocalStoreUrl(path: String = DEFAULT_WORKDIR) extends StoreUrl
    case class TmpUrl(prefix: String)                        extends StoreUrl
    case class MemoryUrl()                                   extends StoreUrl

    val tmpFormat        = "tmp://([a-z0-9\\.\\-]+)/?".r
    val s3formatNoPrefix = "s3://([a-z0-9\\.\\-]+)/?".r
    val s3formatPrefix   = "s3://([a-z0-9\\.\\-]+)/([a-zA-Z0-9/!\\-\\._\\*\\(\\)]+)".r
    val fileFormat       = "file://?(/.*)".r
    val slashFormat      = "(/.*)".r
    val memFormat        = "memory://".r

    implicit val storeUrlDecoder: Decoder[StoreUrl] = Decoder.decodeString.emapTry {
      case tmpFormat(prefix)              => Success(TmpUrl(prefix))
      case fileFormat(path)               => Success(LocalStoreUrl(path))
      case slashFormat(path)              => Success(LocalStoreUrl(path))
      case s3formatNoPrefix(bucket)       => Success(S3StoreUrl(bucket, "nixiesearch"))
      case s3formatPrefix(bucket, prefix) => Success(S3StoreUrl(bucket, prefix))
      case memFormat()                    => Success(MemoryUrl())
      case other                          => Failure(new Exception(s"cannot parse store url '$other'"))
    }
  }
}

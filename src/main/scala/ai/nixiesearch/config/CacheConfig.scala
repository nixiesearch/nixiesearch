package ai.nixiesearch.config

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.*

import java.nio.file.Paths

case class CacheConfig(dir: String = CacheConfig.defaultCacheDir())

object CacheConfig {

  def defaultCacheDir(): String =
    Paths.get(System.getProperty("user.dir"), "cache").toString

  given cacheConfigEncoder: Encoder[CacheConfig] = deriveEncoder
  given cacheConfigDecoder: Decoder[CacheConfig] = Decoder.instance(c =>
    for {
      configDir   <- c.downField("dir").as[Option[String]]
      envOverride <- env("NIXIESEARCH_CORE_CACHE_DIR")
    } yield {
      val dir = (envOverride, configDir) match {
        case (Some(value), _) => value
        case (_, Some(value)) => value
        case (_, _)           => defaultCacheDir()
      }
      CacheConfig(dir)
    }
  )

  private def env(name: String): Decoder.Result[Option[String]] = Option(System.getenv(name)) match {
    case Some(value) => Right(Some(value))
    case None        => Right(None)
  }
}

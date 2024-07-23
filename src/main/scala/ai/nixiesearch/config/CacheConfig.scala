package ai.nixiesearch.config

import ai.nixiesearch.config.CacheConfig.EmbeddingCacheConfig
import ai.nixiesearch.config.CacheConfig.EmbeddingCacheConfig.HeapCache
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.*

import java.io.File
import java.nio.file.Paths

case class CacheConfig(dir: String = CacheConfig.defaultCacheDir(), embeddings: EmbeddingCacheConfig = HeapCache())

object CacheConfig {
  sealed trait EmbeddingCacheConfig
  object EmbeddingCacheConfig {
    case class NoCache()                           extends EmbeddingCacheConfig
    case class HeapCache(maxSize: Int = 32 * 1024) extends EmbeddingCacheConfig
    // case class RocksDBCache()                      extends EmbeddingCacheConfig // TODO
    // case class RedisCache()                      extends EmbeddingCacheConfig // TODO

    given noCacheCodec: Codec[NoCache]         = deriveCodec
    given heapCacheEncoder: Encoder[HeapCache] = deriveEncoder
    given heapCacheDecoder: Decoder[HeapCache] = Decoder.instance(c =>
      for {
        maxSize <- c.downField("maxSize").as[Option[Int]]
      } yield {
        HeapCache(maxSize.getOrElse(32 * 1024))
      }
    )
    given embeddingCacheConfigEncoder: Encoder[EmbeddingCacheConfig] = Encoder.instance {
      case c: NoCache   => Json.obj("none" -> noCacheCodec(c))
      case c: HeapCache => Json.obj("heap" -> heapCacheEncoder(c))
    }
    given embeddingCacheConfigDecoder: Decoder[EmbeddingCacheConfig] = Decoder.instance(c => {
      c.downField("none").as[NoCache] match {
        case Right(noCache) => Right(noCache)
        case Left(_)        => c.downField("heap").as[HeapCache]
      }
    })
  }

  def defaultCacheDir(): String = Option(System.getenv("XDG_CACHE_HOME")) match {
    case Some(cacheDir) => Paths.get(cacheDir, "nixiesearch", "cache").toString
    case None           => Paths.get(System.getProperty("user.dir"), "nixiesearch", "cache").toString
  }

  given cacheConfigEncoder: Encoder[CacheConfig] = deriveEncoder
  given cacheConfigDecoder: Decoder[CacheConfig] = Decoder.instance(c =>
    for {
      configDir   <- c.downField("dir").as[Option[String]]
      envOverride <- env("NIXIESEARCH_CORE_CACHE_DIR")
      embeddings  <- c.downField("embeddings").as[Option[EmbeddingCacheConfig]]
    } yield {
      val dir = (envOverride, configDir) match {
        case (Some(value), _) => value
        case (_, Some(value)) => value
        case (_, _)           => defaultCacheDir()
      }
      CacheConfig(dir, embeddings.getOrElse(HeapCache()))
    }
  )

  private def env(name: String): Decoder.Result[Option[String]] = Option(System.getenv(name)) match {
    case Some(value) => Right(Some(value))
    case None        => Right(None)
  }
}

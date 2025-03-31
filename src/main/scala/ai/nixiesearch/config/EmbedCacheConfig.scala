package ai.nixiesearch.config

import ai.nixiesearch.util.Size
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.*

sealed trait EmbedCacheConfig {}

object EmbedCacheConfig {
  val DEFAULT_CACHE_SIZE = 32 * 1024

  case object NoCache                                           extends EmbedCacheConfig
  case class MemoryCacheConfig(maxSize: Int = DEFAULT_CACHE_SIZE) extends EmbedCacheConfig
  // case class RedisCacheConfig()                         extends EmbedCacheConfig

  given heapCacheConfigEncoder: Encoder[MemoryCacheConfig] = deriveEncoder
  given heapCacheConfigDecoder: Decoder[MemoryCacheConfig] = Decoder.instance(c =>
    for {
      size <- c.downField("max_size").as[Option[Int]]
    } yield {
      MemoryCacheConfig(size.getOrElse(DEFAULT_CACHE_SIZE))
    }
  )

  given embedCacheConfigDecoder: Decoder[EmbedCacheConfig] = Decoder.instance(c =>
    c.as[Boolean] match {
      case Right(true)  => Right(MemoryCacheConfig())
      case Right(false) => Right(NoCache)
      case Left(_) =>
        c.value.asObject match {
          case None => Left(DecodingFailure(s"cache configuration should be bool|obj, but got ${c.value}", c.history))
          case Some(obj) =>
            obj.keys.toList match {
              case head :: Nil =>
                head match {
                  case "none"   => Right(NoCache)
                  case "memory" => c.downField("memory").as[MemoryCacheConfig]
                  case other => Left(DecodingFailure(s"cache type '$other' not supported, try none/memory'", c.history))
                }
              case _ =>
                Left(DecodingFailure(s"cache config should be an object with a single key, got $obj", c.history))
            }
        }
    }
  )

  given embedCacheConfigEncoder: Encoder[EmbedCacheConfig] = Encoder.instance {
    case NoCache            => Json.obj("none" -> Json.obj())
    case c: MemoryCacheConfig => Json.obj("inmem" -> heapCacheConfigEncoder(c))
  }
}

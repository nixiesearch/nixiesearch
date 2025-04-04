package ai.nixiesearch.util.analytics

import ai.nixiesearch.config.Config
import ai.nixiesearch.util.analytics.OnStartAnalyticsPayload.{getMacHash, hash}
import cats.effect.IO
import io.circe.Codec
import io.circe.syntax.*
import io.circe.generic.semiauto.*

case class OnlinePayload(confHash: String, macHash: Option[String], uptime: Long)

object OnlinePayload {
  given onlinePayloadCodec: Codec[OnlinePayload] = deriveCodec

  def create(config: Config, startTime: Long): IO[OnlinePayload] = for {
    confHash <- IO(OnStartAnalyticsPayload.hash(config.asJson.noSpaces))
    macHash  <- getMacHash
    uptime   <- IO(System.currentTimeMillis() - startTime)
  } yield {
    OnlinePayload(confHash, macHash, uptime)
  }
}

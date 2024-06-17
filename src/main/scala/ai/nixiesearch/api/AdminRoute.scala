package ai.nixiesearch.api

import ai.nixiesearch.api.AdminRoute.IndexListResponse
import ai.nixiesearch.config.Config
import ai.nixiesearch.config.mapping.IndexName
import cats.effect.IO
import io.circe.Codec
import org.http4s.{EntityEncoder, HttpRoutes}
import org.http4s.dsl.io.*
import io.circe.syntax.*
import io.circe.generic.semiauto.*
import org.http4s.circe.*

case class AdminRoute(config: Config) extends Route {
  import AdminRoute.given

  val routes = HttpRoutes.of[IO] {
    case GET -> Root / "_config"  => Ok(config)
    case GET -> Root / "_indexes" => Ok(IndexListResponse(config.schema.keys.toList))
    case GET -> Root / "_indices" => Ok(IndexListResponse(config.schema.keys.toList))
  }
}

object AdminRoute {
  case class IndexListResponse(indexes: List[IndexName])
  given indexListResponseCodec: Codec[IndexListResponse] = deriveCodec

  given configJson: EntityEncoder[IO, Config]               = jsonEncoderOf
  given indexListJson: EntityEncoder[IO, IndexListResponse] = jsonEncoderOf
}

package ai.nixiesearch.api

import ai.nixiesearch.config.Config
import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import io.circe.syntax.*

import java.io.File

case class AdminRoute(config: Config) extends Route {
  val routes = HttpRoutes.of[IO] { case GET -> Root / "admin" / "config" => Ok(config.asJson.spaces2) }
}

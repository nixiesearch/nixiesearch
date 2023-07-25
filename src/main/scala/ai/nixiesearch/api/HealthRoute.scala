package ai.nixiesearch.api

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.dsl.io._

case class HealthRoute() extends Route {
  val routes = HttpRoutes.of[IO] { case GET -> Root / "health" => Ok() }
}

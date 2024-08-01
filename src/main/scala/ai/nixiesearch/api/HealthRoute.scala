package ai.nixiesearch.api

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*

case class HealthRoute() extends Route {
  override val routes: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root / "health" =>
    Ok()
  }
}

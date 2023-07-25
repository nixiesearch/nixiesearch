package ai.nixiesearch.api

import cats.effect.IO
import org.http4s.HttpRoutes

trait Route {
  def routes: HttpRoutes[IO]
}

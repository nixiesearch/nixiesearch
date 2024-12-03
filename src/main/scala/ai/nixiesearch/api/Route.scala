package ai.nixiesearch.api

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.IndexMapping.Alias
import cats.effect.IO
import org.http4s.HttpRoutes

trait Route {
  def routes: HttpRoutes[IO]
}

object Route {}

package ai.nixiesearch.api

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.server.websocket.WebSocketBuilder

trait Route {
  def routes: HttpRoutes[IO]
  def wsroutes(wsb: WebSocketBuilder[IO]): HttpRoutes[IO] = HttpRoutes.empty[IO]
}

object Route {}

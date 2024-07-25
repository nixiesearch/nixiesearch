package ai.nixiesearch.api

import ai.nixiesearch.config.ApiConfig.{Hostname, Port}
import ai.nixiesearch.config.Config
import ai.nixiesearch.core.Logging
import ai.nixiesearch.main.Logo
import ai.nixiesearch.main.subcommands.StandaloneMode.{error, info}
import cats.Id
import cats.data.Kleisli
import cats.effect.IO
import cats.effect.kernel.Resource
import com.comcast.ip4s.{Hostname as SHostname, Port as SPort}
import org.http4s.{Header, HttpRoutes, Request, Status}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.{ErrorAction, Logger}
import cats.implicits.*
import org.http4s.Header.ToRaw
import org.http4s.server.middleware.CORS
import org.http4s.server.websocket.WebSocketBuilder
import org.typelevel.ci.CIString

import scala.concurrent.duration.Duration

object API extends Logging {
  // YOLO CORS handling
  def addCORSHeader(routes: HttpRoutes[IO]): HttpRoutes[IO] = Kleisli { (req: Request[IO]) =>
    routes(req).map {
      case Status.Successful(resp) if req.headers.get(CIString("Origin")).isDefined =>
        resp.putHeaders(Header.Raw(CIString("Access-Control-Allow-Origin"), "*"))
      case other => other
    }
  }
  def wrapMiddleware(routes: HttpRoutes[IO]): HttpRoutes[IO] = {
    Logger.httpRoutes(logBody = false, logHeaders = false, logAction = Some(info))(
      ErrorAction.httpRoutes(addCORSHeader(routes), (req, err) => error(err.toString, err))
    )
  }

  def start(
      routes: HttpRoutes[IO],
      wss: WebSocketBuilder[IO] => HttpRoutes[IO],
      host: Hostname,
      port: Port
  ): IO[Resource[IO, org.http4s.server.Server]] = {
    for {
      host <- IO.fromOption(SHostname.fromString(host.value))(
        new Exception(s"cannot parse hostname '${host.value}'")
      )
      port <- IO.fromOption(SPort.fromInt(port.value))(
        new Exception(s"cannot parse port '${port.value}'")
      )
      http = wss.andThen(wsr => Router("/" -> wrapMiddleware(wsr <+> routes)).orNotFound)
      api <- IO(
        EmberServerBuilder
          .default[IO]
          .withHost(host)
          .withPort(port)
          .withHttpWebSocketApp(http)
          .withIdleTimeout(Duration.Inf)
      )

    } yield {
      api.build
    }
  }
}

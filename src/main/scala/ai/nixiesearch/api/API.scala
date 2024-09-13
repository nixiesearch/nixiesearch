package ai.nixiesearch.api

import ai.nixiesearch.config.ApiConfig.{Hostname, Port}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.main.subcommands.StandaloneMode.{error, info}
import cats.data.Kleisli
import cats.effect.IO
import cats.effect.kernel.Resource
import com.comcast.ip4s.{Hostname as SHostname, Port as SPort}
import org.http4s.{HttpApp, HttpRoutes, Response}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.{ErrorAction, Logger}
import cats.implicits.*
import org.typelevel.ci.CIString

import scala.concurrent.duration.Duration

object API extends Logging {

  def wrapMiddleware(routes: HttpRoutes[IO]): HttpApp[IO] = {
    val withMiddleware = Logger.httpRoutes(logBody = false, logHeaders = false, logAction = Some(info))(
      ErrorAction.httpRoutes(routes, (req, err) => error(err.toString, err))
    )
    Router("/" -> withMiddleware).orNotFound.handleErrorWith(ErrorHandler.handle)
  }

  def start(
      routes: HttpRoutes[IO],
      host: Hostname,
      port: Port
  ): Resource[IO, org.http4s.server.Server] = {
    for {
      host <- Resource.eval(
        IO.fromOption(SHostname.fromString(host.value))(
          new Exception(s"cannot parse hostname '${host.value}'")
        )
      )
      port <- Resource.eval(
        IO.fromOption(SPort.fromInt(port.value))(
          new Exception(s"cannot parse port '${port.value}'")
        )
      )
      http = wrapMiddleware(routes)
      api <- EmberServerBuilder
        .default[IO]
        .withHost(host)
        .withPort(port)
        .withHttpApp(http)
        .withIdleTimeout(Duration.Inf)
        .build
    } yield {
      api
    }
  }

}

package ai.nixiesearch.api

import ai.nixiesearch.config.ApiConfig.{Hostname, Port}
import ai.nixiesearch.config.Config
import ai.nixiesearch.core.Logging
import ai.nixiesearch.main.Logo
import ai.nixiesearch.main.subcommands.StandaloneMode.{error, info}
import cats.effect.IO
import cats.effect.kernel.Resource
import com.comcast.ip4s.{Hostname as SHostname, Port as SPort}
import org.http4s.HttpRoutes
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.{ErrorAction, Logger}
import cats.implicits.*
import org.http4s.server.middleware.CORS

import scala.concurrent.duration.Duration

object API extends Logging {
  def wrapMiddleware(routes: HttpRoutes[IO]): IO[HttpRoutes[IO]] = for {
    routesWithError <- IO(ErrorAction.httpRoutes(routes, (req, err) => error(err.toString, err)))
    routesWithLog <- IO(
      Logger.httpRoutes(logBody = false, logHeaders = false, logAction = Some(info))(routesWithError)
    )
    corsMiddleware <- CORS.policy.withAllowOriginAll(routesWithLog)
  } yield {
    corsMiddleware
  }

  def start(routes: HttpRoutes[IO], host: Hostname, port: Port): IO[Resource[IO, org.http4s.server.Server]] = for {
    withMiddlewareRoutes <- wrapMiddleware(routes)
    http                 <- IO(Router("/" -> withMiddlewareRoutes).orNotFound)
    host <- IO.fromOption(SHostname.fromString(host.value))(
      new Exception(s"cannot parse hostname '${host.value}'")
    )
    port <- IO.fromOption(SPort.fromInt(port.value))(
      new Exception(s"cannot parse port '${port.value}'")
    )
    api <- IO(
      EmberServerBuilder
        .default[IO]
        .withHost(host)
        .withPort(port)
        .withHttpApp(http)
        .withIdleTimeout(Duration.Inf)
    )

  } yield {
    api.build
  }
}

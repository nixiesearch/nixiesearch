package ai.nixiesearch.api

import ai.nixiesearch.config.Config
import ai.nixiesearch.core.Logging
import ai.nixiesearch.main.Logo
import ai.nixiesearch.main.subcommands.StandaloneMode.{error, info}
import cats.effect.IO
import cats.effect.kernel.Resource
import com.comcast.ip4s.{Hostname, Port}
import org.http4s.HttpRoutes
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.{ErrorAction, Logger}
import cats.implicits.*
import scala.concurrent.duration.Duration

object API extends Logging {
  def start(routes: HttpRoutes[IO], config: Config): IO[Resource[IO, org.http4s.server.Server]] = for {
    routesWithError <- IO(ErrorAction.httpRoutes(routes, (req, err) => error(err.toString, err)))
    routesWithLog <- IO(
      Logger.httpRoutes(logBody = false, logHeaders = false, logAction = Some(info))(routesWithError)
    )
    http <- IO(Router("/" -> routesWithLog).orNotFound)
    host <- IO.fromOption(Hostname.fromString(config.api.host.value))(
      new Exception(s"cannot parse hostname '${config.api.host.value}'")
    )
    port <- IO.fromOption(Port.fromInt(config.api.port.value))(
      new Exception(s"cannot parse port '${config.api.port.value}'")
    )
    _ <- Logo.lines.map(line => info(line)).sequence
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

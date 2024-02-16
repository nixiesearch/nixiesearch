package ai.nixiesearch.main

import ai.nixiesearch.api.{HealthRoute, IndexRoute, SearchRoute, SuggestRoute, WebuiRoute}
import ai.nixiesearch.config.Config
import ai.nixiesearch.config.StoreConfig.{LocalStoreConfig, MemoryStoreConfig}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.IndexRegistry
import ai.nixiesearch.main.CliConfig.CliArgs.StandaloneArgs
import cats.effect.IO
import org.http4s.server.Router
import cats.implicits.*
import com.comcast.ip4s.{Hostname, Port}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.ErrorAction
import org.http4s.server.middleware.Logger
import scala.concurrent.duration.Duration

object Standalone extends Logging {
  def run(args: StandaloneArgs): IO[Unit] = for {
    config  <- Config.load(args.config)
    indices <- IO(config.search.values.toList ++ config.suggest.values.map(_.index).toList)
    store <- config.store match {
      case s: LocalStoreConfig  => IO.pure(s)
      case s: MemoryStoreConfig => IO.pure(s)
      case other                => IO.raiseError(new Exception(s"store $other not supported"))
    }
    _ <- IndexRegistry
      .create(store, config.core.cache.embedding, indices)
      .use(registry =>
        for {
          search          <- IO(SearchRoute(registry))
          index           <- IO(IndexRoute(registry))
          suggest         <- IO(SuggestRoute(registry, config.suggest))
          health          <- IO(HealthRoute())
          ui              <- WebuiRoute.create(registry, search, suggest, config)
          routes          <- IO(search.routes <+> suggest.routes <+> index.routes <+> health.routes <+> ui.routes)
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
          _ <- api.build.use(_ => IO.never)

        } yield {}
      )

  } yield {}
}

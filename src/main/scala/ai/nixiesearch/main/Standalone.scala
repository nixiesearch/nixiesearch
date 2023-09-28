package ai.nixiesearch.main

import ai.nixiesearch.api.{HealthRoute, IndexRoute, SearchRoute, SuggestRoute}
import ai.nixiesearch.config.Config
import ai.nixiesearch.config.StoreConfig.{LocalStoreConfig, MemoryStoreConfig}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.IndexRegistry
import ai.nixiesearch.main.CliConfig.CliArgs.StandaloneArgs
import cats.effect.IO
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import cats.implicits.*

object Standalone extends Logging {
  def run(args: StandaloneArgs): IO[Unit] = for {
    config  <- Config.load(args.config)
    indices <- IO(config.search.values.toList ++ config.suggest.values.toList)
    store <- config.store match {
      case s: LocalStoreConfig  => IO.pure(s)
      case s: MemoryStoreConfig => IO.pure(s)
      case other                => IO.raiseError(new Exception(s"store $other not supported"))
    }
    _ <- IndexRegistry
      .create(store, indices)
      .use(registry =>
        for {
          search  <- IO(SearchRoute(registry))
          index   <- IO(IndexRoute(registry))
          suggest <- IO(SuggestRoute(registry))
          health  <- IO(HealthRoute())
          routes  <- IO(search.routes <+> index.routes <+> suggest.routes <+> health.routes)
          http    <- IO(Router("/" -> routes).orNotFound)
          api <- IO(
            BlazeServerBuilder[IO]
              .bindHttp(config.api.port.value, config.api.host.value)
              .withHttpApp(http)
              .withBanner(Logo.lines)
          )
          _ <- api.serve.compile.drain

        } yield {}
      )

  } yield {}
}

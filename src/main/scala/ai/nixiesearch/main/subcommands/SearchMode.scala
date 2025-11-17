package ai.nixiesearch.main.subcommands

import ai.nixiesearch.api.*
import ai.nixiesearch.config.Config
import ai.nixiesearch.core.metrics.Metrics
import ai.nixiesearch.index.{Models, Searcher}
import ai.nixiesearch.index.sync.Index
import ai.nixiesearch.main.CliConfig.ApiMode
import ai.nixiesearch.main.CliConfig.CliArgs.SearchArgs
import ai.nixiesearch.main.Logo
import ai.nixiesearch.main.subcommands.util.LambdaRuntimeClient.{ApiGatewayV2Request, ApiGatewayV2Response}
import ai.nixiesearch.main.subcommands.util.{LambdaRuntimeClient, PeriodicEvalStream}
import ai.nixiesearch.util.EnvVars
import ai.nixiesearch.util.analytics.AnalyticsReporter
import cats.data.Kleisli
import cats.effect.kernel.Resource
import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import org.http4s.{HttpApp, HttpRoutes}

import scala.concurrent.duration.*

object SearchMode extends Mode[SearchArgs] {
  case class SearchInit(routes: HttpRoutes[IO], config: Config)

  def run(args: SearchArgs, env: EnvVars): IO[Unit] = {
    init(args, env).use(app =>
      args.api match {
        case ApiMode.Http   => serveHttp(app.routes, app.config)
        case ApiMode.Lambda => serveLambda(app.routes, app.config, env)
      }
    )

  }

  def init(args: SearchArgs, env: EnvVars): Resource[IO, SearchInit] = for {
    _      <- Resource.eval(info(s"Starting in '${args.mode}' mode with only searcher"))
    config <- Resource.eval(Config.load(args.config, env))
    _      <- AnalyticsReporter.create(config, args.mode)

    metrics   <- Resource.pure(Metrics())
    models    <- Models.create(config.inference, config.core.cache, metrics, env)
    searchers <- config.schema.values.toList
      .map(im =>
        for {
          index    <- Index.forSearch(im, models)
          searcher <- Searcher.open(index, metrics)
          _        <- PeriodicEvalStream.run(
            every = index.mapping.config.indexer.flush.interval,
            action = index.sync().flatMap {
              case false => IO.unit
              case true  => searcher.sync()
            }
          )
        } yield {
          searcher
        }
      )
      .sequence
    searchRoutes = searchers.map(s => SearchRoute(s).routes <+> MappingRoute(s.index).routes <+> StatsRoute(s).routes)
    routes       = List(
      searchRoutes,
      List(HealthRoute().routes),
      List(AdminRoute(config).routes),
      List(MainRoute().routes),
      List(TypicalErrorsRoute(searchers.map(_.index.name.value)).routes),
      List(InferenceRoute(models).routes),
      List(MetricsRoute(metrics).routes)
    ).flatten.reduce(_ <+> _)
    _ <- Resource.eval(info(s"index init done: indexes=${searchers.map(s => s.index.name.value)}"))
    _ <- Resource.eval(Logo.lines.map(line => info(line)).sequence)
  } yield {
    SearchInit(routes, config)
  }

  def serveHttp(routes: HttpRoutes[IO], config: Config): IO[Unit] =
    API.start(routes, config.core.host, config.core.port).use(_ => IO.never)

  def serveLambda(routes: HttpRoutes[IO], config: Config, env: EnvVars): IO[Unit] = {
    for {
      httpApp <- API.wrapMiddleware(routes)

      _ <- Stream
        .resource(LambdaRuntimeClient.create(env))
        .flatMap(client =>
          Stream
            .repeatEval(client.waitForNextInvocation())
            .evalMap(request =>
              processLambdaRequest(httpApp, request).attempt.flatMap {
                case Left(error) =>
                  client.postInvocationError(request.requestContext.requestId, error)
                case Right(response) =>
                  client.postInvocationResponse(request.requestContext.requestId, response)
              }
            )
        )
        .compile
        .drain
    } yield {}

  }

  def processLambdaRequest(routes: HttpApp[IO], rawRequest: ApiGatewayV2Request): IO[ApiGatewayV2Response] = for {
    _           <- debug(s"got lambda request: $rawRequest")
    request     <- rawRequest.toHttp4s()
    _           <- debug(s"http4s lambda request: $request")
    response    <- routes.run(request)
    rawResponse <- ApiGatewayV2Response.fromResponse(response)
    _           <- debug(s"response: ${rawResponse}")
  } yield {
    rawResponse
  }

}

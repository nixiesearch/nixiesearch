package ai.nixiesearch.util.analytics

import ai.nixiesearch.config.Config
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.util.EnvVars
import ai.nixiesearch.util.analytics.AnalyticsReporter.{AnalyticsPayload, ONLINE_GID, START_GID, posthogJson}
import cats.effect.IO
import cats.effect.kernel.Resource
import io.circe.{Encoder, Json}
import io.circe.generic.semiauto.*
import org.http4s.{EntityEncoder, Headers, MediaType, Method, Request, Uri}
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`
import org.http4s.circe.*
import org.http4s.ember.client.EmberClientBuilder
import fs2.Stream
import scala.concurrent.duration.*
import java.time.Instant
import java.time.format.DateTimeFormatter

case class AnalyticsReporter(
    client: Client[IO],
    endpoint: Uri,
    config: Config,
    startTime: Long,
    mode: String
) extends Logging {
  def onStart(): IO[Unit] = for {
    payload <- OnStartAnalyticsPayload.create(config, mode)
    _       <- post("init", START_GID, payload)
  } yield {}

  def onUptime(): IO[Unit] = for {
    payload <- OnlinePayload.create(config, startTime)
    _       <- post("online", ONLINE_GID, payload)
  } yield {}

  private def post[T](event: String, gid: String, payload: T)(using encoder: Encoder[T]): IO[Unit] = for {
    start <- IO(System.currentTimeMillis())
    _ <- IO.whenA(config.core.telemetry.usage)(
      client
        .stream(
          Request[IO](
            method = Method.POST,
            uri = endpoint.withQueryParam("gid", gid),
            headers = Headers(`Content-Type`(MediaType.application.json)),
            entity = AnalyticsReporter.posthogJson.toEntity(
              AnalyticsPayload(
                event = event,
                properties = encoder(payload).noSpaces,
                timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(System.currentTimeMillis()))
              )
            )
          )
        )
        .evalMap(response =>
          response.status.code match {
            case 200 | 302 =>
              info(s"Submitted '${event}' usage statistics event (${System.currentTimeMillis() - start}ms)")
            case _ =>
              response.entity.body.compile.toList
                .map(x => new String(x.toArray))
                .flatMap(str => error(s"Error submitting '${event}' usage statistics event: $str"))
          }
        )
        .compile
        .drain
    )
  } yield {}
}

object AnalyticsReporter extends Logging {
  val START_GID  = "1685497662"
  val ONLINE_GID = "258792152"
  val WEBHOOK_ENDPOINT =
    "https://script.google.com/macros/s/AKfycbxTQ0cP4ISZWYVlwnt88kKnthljqInQnD7CnXr-KTLoB4WkGD0n21NyMhBXl0pHnCeLgw/exec"
  val WEBHOOK_TIMEOUT      = 10.seconds
  val ONLINE_SUBMIT_PERIOD = 4.hours

  case class AnalyticsPayload(event: String, properties: String, timestamp: String)
  given posthogEncoder: Encoder[AnalyticsPayload]        = deriveEncoder
  given posthogJson: EntityEncoder[IO, AnalyticsPayload] = jsonEncoderOf

  def create(config: Config, mode: String): Resource[IO, AnalyticsReporter] = for {
    enabled   <- Resource.pure(config.core.telemetry.usage)
    _         <- Resource.eval(info(s"Usage statistics collection: ${if (enabled) "ENABLED" else "DISABLED"}"))
    endpoint  <- Resource.eval(IO.fromEither(Uri.fromString(WEBHOOK_ENDPOINT)))
    client    <- EmberClientBuilder.default[IO].withTimeout(WEBHOOK_TIMEOUT).build
    startTime <- Resource.eval(IO(System.currentTimeMillis()))
    reporter  <- Resource.pure(AnalyticsReporter(client, endpoint, config, startTime, mode))
    _ <- Stream
      .iterate[IO, Int](0)(_ + 1)
      .meteredStartImmediately(ONLINE_SUBMIT_PERIOD)
      .evalTap {
        case 0 => reporter.onStart()
        case _ => reporter.onUptime()
      }
      .compile
      .drain
      .background
  } yield {
    reporter
  }

}

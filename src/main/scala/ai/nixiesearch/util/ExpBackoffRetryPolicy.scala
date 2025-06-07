package ai.nixiesearch.util

import cats.effect.IO
import org.http4s.{Request, Response}
import org.http4s.client.middleware.RetryPolicy

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

case class ExpBackoffRetryPolicy(
    initialDelay: FiniteDuration,
    multiplier: Double,
    maxDuration: FiniteDuration,
    maxRetries: Int
) extends RetryPolicy[IO] {
  override def apply(
      request: Request[IO],
      response: Either[Throwable, Response[IO]],
      failures: Int
  ): Option[FiniteDuration] = {
    if (RetryPolicy.defaultRetriable(request, response)) {
      val millis =
        math.round(math.min(initialDelay.toMillis * math.pow(multiplier, failures), maxDuration.toMillis.toDouble))
      if (failures < maxRetries) {
        Some(FiniteDuration(millis, TimeUnit.MILLISECONDS))
      } else {
        None
      }
    } else {
      None
    }
  }

}

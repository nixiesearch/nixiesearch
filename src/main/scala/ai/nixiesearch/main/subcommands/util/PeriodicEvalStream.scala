package ai.nixiesearch.main.subcommands.util

import cats.effect.{IO, OutcomeIO, ResourceIO}
import fs2.Stream

import scala.concurrent.duration.FiniteDuration

object PeriodicEvalStream {
  def run[T](action: IO[T], every: FiniteDuration): ResourceIO[IO[OutcomeIO[Unit]]] = Stream
    .repeatEval(action)
    .metered(every)
    .compile
    .drain
    .background

}

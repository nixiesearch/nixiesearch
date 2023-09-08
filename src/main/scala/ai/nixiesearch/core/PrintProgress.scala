package ai.nixiesearch.core

import cats.effect.IO
import fs2.Pipe

object PrintProgress extends Logging {
  case class ProgressPeriod(
      start: Long = System.currentTimeMillis(),
      total: Int = 0,
      batchTotal: Int = 0
  ) {
    def inc(events: Int) =
      copy(total = total + events, batchTotal = batchTotal + events)
  }

  def tap[T](suffix: String): Pipe[IO, T, T] = input =>
    input.scanChunks(ProgressPeriod()) { case (pp @ ProgressPeriod(start, total, batch), next) =>
      val now = System.currentTimeMillis()
      if ((now - start > 1000)) {
        val timeDiffSeconds = (now - start) / 1000.0
        val perf            = math.round(batch / timeDiffSeconds)
        logger.info(
          s"processed ${total} $suffix, perf=${perf}rps"
        )
        (
          pp.copy(start = now, batchTotal = 0).inc(next.size),
          next
        )
      } else {
        (pp.inc(next.size), next)
      }
    }
}

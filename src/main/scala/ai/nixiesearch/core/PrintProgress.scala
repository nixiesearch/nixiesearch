package ai.nixiesearch.core

import cats.effect.IO
import fs2.{Chunk, Pipe}
import org.apache.commons.io.FileUtils

object PrintProgress extends Logging {
  case class ProgressPeriod(
      start: Long = System.currentTimeMillis(),
      total: Long = 0L,
      batchTotal: Long = 0L
  ) {
    def inc(events: Int) =
      copy(total = total + events, batchTotal = batchTotal + events)
  }

  def bytes[T]: Pipe[IO, T, T] = input =>
    input.scanChunks(ProgressPeriod()) { case (pp @ ProgressPeriod(start, total, batch), next) =>
      val now = System.currentTimeMillis()
      if ((now - start > 1000)) {
        val timeDiffSeconds = (now - start) / 1000.0
        val perf            = math.round(batch / timeDiffSeconds)
        val totalHuman      = FileUtils.byteCountToDisplaySize(total)
        val perfHuman       = FileUtils.byteCountToDisplaySize(perf)
        logger.info(
          s"processed $totalHuman, rate: ${perfHuman}/s"
        )
        (
          pp.copy(start = now, batchTotal = 0).inc(next.size),
          next
        )
      } else {
        (pp.inc(next.size), next)
      }
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

  def tapChunk[T](suffix: String): Pipe[IO, Chunk[T], Chunk[T]] = input =>
    input.scanChunks(ProgressPeriod()) { case (pp @ ProgressPeriod(start, total, batch), next) =>
      val now      = System.currentTimeMillis()
      val nextSize = next.foldLeft(0)((cnt, n) => cnt + n.size)
      if ((now - start > 1000)) {
        val timeDiffSeconds = (now - start) / 1000.0
        val perf            = math.round(batch / timeDiffSeconds)
        logger.info(
          s"processed ${total} $suffix, perf=${perf}rps"
        )
        (
          pp.copy(start = now, batchTotal = 0).inc(nextSize),
          next
        )
      } else {
        (pp.inc(nextSize), next)
      }
    }
}

package ai.nixiesearch.util

import cats.effect.IO
import fs2.{Pipe, Pull, Stream}

object DurationStream {
  def pipe[T](start: Long): Pipe[IO, T, (T, Long)] = in => pipeRec(in, start).stream

  private def pipeRec[T](s: Stream[IO, T], prev: Long): Pull[IO, (T, Long), Unit] =
    s.pull.uncons1.flatMap {
      case None =>
        Pull.done
      case Some((head, tail)) =>
        val now = System.currentTimeMillis()
        Pull.output1((head, now - prev)) >> pipeRec(tail, now)
    }
}

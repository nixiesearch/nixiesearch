package ai.nixiesearch.util

import cats.effect.IO
import fs2.{Pipe, Stream, Pull}

object StreamMark {
  def pipe[T](first: T => T = identity[T], tail: T => T = identity[T]): Pipe[IO, T, T] =
    in => pipeRec(in, None, first, tail).stream

  private def pipeRec[T](
      s: Stream[IO, T],
      prev: Option[T],
      first: T => T,
      last: T => T
  ): Pull[IO, T, Unit] =
    s.pull.uncons1.flatMap {
      case None =>
        prev match {
          case None =>
            // empty stream
            Pull.done
          case Some(lastItem) =>
            // empty tail, so it's last item
            Pull.output1(last(lastItem)) >> Pull.done
        }
      case Some((head, tail)) =>
        prev match {
          case None =>
            // first item - buffer it
            pipeRec(tail, prev = Some(first(head)), first, last)
          case Some(next) =>
            // middle item
            Pull.output1(next) >> pipeRec(tail, prev = Some(head), first, last)
        }
    }

}

package ai.nixiesearch.util

import cats.effect.IO
import fs2.{Pipe, Stream, Pull}

object StreamMarkLast {
  def pipe[T](mark: T => T): Pipe[IO, T, T] = in => onLast(in, None, mark).stream

  private def onLast[T](s: Stream[IO, T], prev: Option[T], mark: T => T): Pull[IO, T, Unit] =
    s.pull.uncons1.flatMap {
      case None =>
        prev match {
          case None       => Pull.done
          case Some(last) => Pull.output1(mark(last)) >> Pull.done
        }
      case Some((head, tail)) =>
        prev match {
          case None       => onLast(tail, prev = Some(head), mark)
          case Some(last) => Pull.output1(last) >> onLast(tail, prev = Some(head), mark)
        }
    }

}

package ai.nixiesearch.core.nn.model.generative

import cats.effect.IO
import fs2.{Pipe, Pull, Stream}
import io.circe.Decoder
import io.circe.parser.decode

object SSEParser {
  val prefix = "data: "
  def parse[T: Decoder]: Pipe[IO, String, T] = {
    def go(stream: Stream[IO, String]): Pull[IO, T, Unit] = stream.pull.uncons1.flatMap {
      case Some((line, tail)) if line.startsWith(prefix) =>
        val payload = line.drop(prefix.length)
        payload match {
          case "[DONE]" => Pull.done
          case _ =>
            decode[T](payload) match {
              case Left(value)  => Pull.raiseError[IO](new Exception(value))
              case Right(value) => Pull.output1(value) >> go(tail)
            }
        }
      case Some((line, tail)) if line.isEmpty => go(tail)
      case Some((line, tail))                 => Pull.raiseError[IO](new Exception("SSE event header mismatch"))
      case None                               => Pull.done
    }
    in => go(in).stream
  }

}

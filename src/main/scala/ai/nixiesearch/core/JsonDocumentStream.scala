package ai.nixiesearch.core

import cats.effect.IO
import fs2.{Pipe, Pull, Stream}
import fs2.Chunk
import io.circe.Json
import org.typelevel.jawn.AsyncParser
import org.typelevel.jawn.AsyncParser.{Mode, UnwrapArray, ValueStream}

object JsonDocumentStream extends Logging {

  import io.circe.jawn.CirceSupportParser.facade

  def parse: Pipe[IO, Byte, Document] = bytes =>
    bytes.pull.peek1.flatMap {
      case Some(('[', tail)) => tail.through(parse(UnwrapArray)).pull.echo
      case Some((_, tail))   => tail.through(parse(ValueStream)).pull.echo
      case None              => Pull.done
    }.stream

  private def parse(mode: Mode): Pipe[IO, Byte, Document] = bytes =>
    bytes
      .scanChunks(AsyncParser[Json](mode))((parser, next) => {
        parser.absorb(next.toByteBuffer) match {
          case Left(value) =>
            logger.error(s"Cannot parse json input: '${new String(next.toArray)}'", value)
            throw value
          case Right(value) => (parser, Chunk.seq(value))
        }
      })
      .evalMapChunk(json =>
        IO(json.as[Document]).flatMap {
          case Left(err)    => error(s"cannot decode json $json", err) *> IO.raiseError(err)
          case Right(event) => IO.pure(event)
        }
      )
      .chunkN(1024)
      .flatMap(x => Stream.emits(x.toList))

}

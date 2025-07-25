package ai.nixiesearch.core

import ai.nixiesearch.config.mapping.IndexMapping
import cats.effect.IO
import de.lhns.fs2.compress.{Bzip2Decompressor, GzipDecompressor, ZstdDecompressor}
import fs2.{Pipe, Pull, Stream}
import fs2.Chunk
import io.circe.{Codec, Json}
import org.typelevel.jawn.AsyncParser
import org.typelevel.jawn.AsyncParser.{Mode, UnwrapArray, ValueStream}

object JsonDocumentStream extends Logging {

  import io.circe.jawn.CirceSupportParser.facade
  lazy val gzip: GzipDecompressor[IO] = GzipDecompressor.make()
  given bzip2: Bzip2Decompressor[IO]  = Bzip2Decompressor.make()
  given zstd: ZstdDecompressor[IO]    = ZstdDecompressor.make()

  def parse(mapping: IndexMapping): Pipe[IO, Byte, Document] = {
    given documentCodec: Codec[Document] = Document.codecFor(mapping)
    bytes =>
      bytes.pull.peek.flatMap {
        case Some((chunk, tail)) if chunk.startsWith(List(31.toByte, -117.toByte)) =>
          logger.debug("Got GZIP header x1F x8B, decompressing stream")
          tail.through(gzip.decompress).pull.peek1.flatMap {
            case Some(('[', tail2)) =>
              logger.debug("Payload starts with '[', assuming json-array format")
              tail2.through(parse(UnwrapArray)).pull.echo
            case Some((_, tail2)) => tail2.through(parse(ValueStream)).pull.echo
            case None             => Pull.done
          }
        case Some((chunk, tail)) if chunk.startsWith(List('B'.toByte, 'Z'.toByte)) =>
          logger.debug("Got BZIP2 header BZ, decompressing stream")
          tail.through(bzip2.decompress).pull.peek1.flatMap {
            case Some(('[', tail2)) =>
              logger.debug("Payload starts with '[', assuming json-array format")
              tail2.through(parse(UnwrapArray)).pull.echo
            case Some((_, tail2)) => tail2.through(parse(ValueStream)).pull.echo
            case None             => Pull.done
          }
        case Some((chunk, tail)) if chunk.startsWith(List(40.toByte, -75.toByte)) =>
          logger.debug("Got zstd header xFD x2B, decompressing stream")
          tail.through(zstd.decompress).pull.peek1.flatMap {
            case Some(('[', tail2)) =>
              logger.debug("Payload starts with '[', assuming json-array format")
              tail2.through(parse(UnwrapArray)).pull.echo
            case Some((_, tail2)) => tail2.through(parse(ValueStream)).pull.echo
            case None             => Pull.done
          }
        case Some((chunk, tail)) if chunk.startsWith(List('['.toByte)) =>
          logger.debug("Payload starts with '[', assuming json-array format")
          tail.through(parse(UnwrapArray)).pull.echo
        case Some((chunk, tail)) =>
          tail.through(parse(ValueStream)).pull.echo
        case None => Pull.done
      }.stream
  }

  private def parse(mode: Mode)(using Codec[Document]): Pipe[IO, Byte, Document] = bytes =>
    bytes
      .scanChunks(AsyncParser[Json](mode))((parser, next) => {
        parser.absorb(next.toByteBuffer) match {
          case Left(value) =>
            logger.error(s"Cannot parse json input: '${new String(next.toArray)}'", value)
            throw value
          case Right(value) => (parser, Chunk.from(value))
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

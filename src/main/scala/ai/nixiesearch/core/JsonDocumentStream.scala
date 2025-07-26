package ai.nixiesearch.core

import ai.nixiesearch.config.mapping.IndexMapping
import cats.effect.IO
import de.lhns.fs2.compress.{Bzip2Decompressor, GzipDecompressor, ZstdDecompressor}
import fs2.{Pipe, Pull, Stream}
import fs2.Chunk
import fs2.io.{readInputStream, writeOutputStream}
import io.circe.{Codec, Json}
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import org.typelevel.jawn.AsyncParser
import org.typelevel.jawn.AsyncParser.{Mode, UnwrapArray, ValueStream}

import java.io.{File, FileInputStream, FileOutputStream, InputStream}
import java.nio.file.Files

object JsonDocumentStream extends Logging {

  import io.circe.jawn.CirceSupportParser.facade
  lazy val gzip: GzipDecompressor[IO] = GzipDecompressor.make()
  given bzip2: Bzip2Decompressor[IO]  = Bzip2Decompressor.make()
  given zstd: ZstdDecompressor[IO]    = ZstdDecompressor.make()

  def parse(mapping: IndexMapping): Pipe[IO, Byte, Document] = {
    given documentCodec: Codec[Document] = Document.codecFor(mapping)
    bytes =>
      bytes
        .through(maybeDecompress)
        .pull
        .peek1
        .flatMap {
          case Some((byte, tail)) if byte == '['.toByte =>
            logger.debug("Payload starts with '[', assuming json-array format")
            tail.through(parse(UnwrapArray)).pull.echo
          case Some((chunk, tail)) =>
            tail.through(parse(ValueStream)).pull.echo
          case None => Pull.done
        }
        .stream
  }

  val GZIP_HEADER = List(0x1f, 0x8b).map(_.toByte)
  val ZSTD_HEADER = List(0x28, 0xb5, 0x2f, 0xfd).map(_.toByte)
  val BZ2_HEADER  = List(0x42, 0x5a, 0x68).map(_.toByte)

  def maybeDecompress: Pipe[IO, Byte, Byte] =
    bytes =>
      bytes.pull.peek.flatMap {
        case Some((chunk, tail)) if chunk.startsWith(GZIP_HEADER) => tail.through(gzip.decompress).pull.echo
        case Some((chunk, tail)) if chunk.startsWith(ZSTD_HEADER) => tail.through(zstd.decompress).pull.echo
        case Some((chunk, tail)) if chunk.startsWith(BZ2_HEADER)  => tail.through(bzip2.decompress).pull.echo
        case Some((_, tail))                                      => tail.pull.echo
        case None                                                 => Pull.done
      }.stream

  def decompressDisk(source: Stream[IO, Byte], decompressor: File => InputStream): Stream[IO, Byte] = for {
    tempFile <- Stream.eval(IO(Files.createTempFile("decompress", ".jsonl")))
    _        <- Stream.eval(source.through(writeOutputStream(IO(new FileOutputStream(tempFile.toFile)))).compile.drain)
    byte     <- readInputStream[IO](IO(decompressor(tempFile.toFile)), 1024 * 1024)
  } yield {
    byte
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

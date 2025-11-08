package ai.nixiesearch.core

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.DocumentDecoder.JsonError
import cats.effect.IO
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec, readFromByteBuffer, readFromString}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
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
import java.nio.ByteBuffer
import java.nio.file.Files
import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.Try

object JsonDocumentStream extends Logging {

  lazy val gzip: GzipDecompressor[IO] = GzipDecompressor.make()
  given bzip2: Bzip2Decompressor[IO]  = Bzip2Decompressor.make()
  given zstd: ZstdDecompressor[IO]    = ZstdDecompressor.make()

  def parse(mapping: IndexMapping): Pipe[IO, Byte, Document] = {
    given documentCodec: JsonValueCodec[Document]           = DocumentDecoder.codec(mapping)
    given documentListCodec: JsonValueCodec[List[Document]] = JsonCodecMaker.make[List[Document]]

    bytes =>
      bytes
        .through(maybeDecompress)
        .pull
        .peek1
        .flatMap {
          case Some((byte, tail)) if byte == '['.toByte =>
            logger.debug("Payload starts with '[', assuming JSON array format")
            tail.through(parseJSONArr(documentListCodec)).pull.echo
          case Some((byte, tail)) if byte == '{'.toByte =>
            tail.through(parseNDJSON(documentCodec)).pull.echo
          case Some((byte, _)) =>
            Pull.raiseError[IO](JsonError(s"payload should start with [ or {, but got 0x${byte.toInt.toHexString}"))
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
        case Some((chunk, tail)) if chunk.startsWith(GZIP_HEADER) =>
          logger.info("Detected GZIP compression")
          tail.through(gzip.decompress).pull.echo
        case Some((chunk, tail)) if chunk.startsWith(ZSTD_HEADER) =>
          logger.info("Detected ZSTD compression")
          tail.through(zstd.decompress).pull.echo
        case Some((chunk, tail)) if chunk.startsWith(BZ2_HEADER) =>
          logger.info("Detected BZ2 compression")
          tail.through(bzip2.decompress).pull.echo
        case Some((chunk, tail)) => tail.pull.echo
        case None                => Pull.done
      }.stream

  private def parseNDJSON(decoder: JsonValueCodec[Document]): Pipe[IO, Byte, Document] = bytes =>
    bytes
      .through(fs2.text.utf8.decode)
      .through(fs2.text.lines)
      .filter(_.nonEmpty)
      .parEvalMap(8)(str => decode(str)(using decoder))


  private def parseJSONArr(decoder: JsonValueCodec[List[Document]]): Pipe[IO, Byte, Document] = bytes =>
    bytes
      .through(fs2.text.utf8.decode)
      .reduce(_ + _)
      .evalMap(str => decode[List[Document]](str)(using decoder))
      .flatMap(list => Stream.emits(list))

  private def decode[T](in: String)(using codec: JsonValueCodec[T]): IO[T] =
    IO(readFromString(in)).flatMap {
      case null  => IO.raiseError(JsonError("got null"))
      case value => IO.pure(value)
    }

}

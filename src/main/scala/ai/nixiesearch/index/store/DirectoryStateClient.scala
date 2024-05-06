package ai.nixiesearch.index.store

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.manifest.IndexManifest.IndexFile
import ai.nixiesearch.index.store.StateClient.StateError
import ai.nixiesearch.index.store.StateClient.StateError.*
import cats.effect.{IO, Resource}
import org.apache.lucene.store.{Directory, IOContext, IndexInput}
import fs2.{Chunk, Stream}
import io.circe.parser.*
import cats.implicits.*

import java.nio.ByteBuffer
import java.nio.file.{FileAlreadyExistsException, NoSuchFileException}
import java.time.Instant

case class DirectoryStateClient(dir: Directory, indexName: String) extends StateClient with Logging {
  val IO_BUFFER_SIZE = 16 * 1024L

  override def createManifest(mapping: IndexMapping, seqnum: Long): IO[IndexManifest] = for {
    files <- IO(dir.listAll())
    now   <- IO(Instant.now().toEpochMilli)
    entries <- Stream
      .emits(files)
      .evalMap(file => IO(IndexFile(file, now)))
      .compile
      .toList
  } yield {
    IndexManifest(mapping, entries, seqnum)
  }

  override def readManifest(): IO[Option[IndexManifest]] = {
    IO(dir.listAll().contains(IndexManifest.MANIFEST_FILE_NAME)).flatMap {
      case false => IO.none
      case true =>
        for {
          manifestBytes <- read(IndexManifest.MANIFEST_FILE_NAME).compile.to(Array)
          manifest <- IO(decode[IndexManifest](new String(manifestBytes))).flatMap {
            case Left(err)    => IO.raiseError(err)
            case Right(value) => IO.pure(value)
          }
          _ <- debug(s"read ${IndexManifest.MANIFEST_FILE_NAME} for index '${manifest.mapping.name}'")
        } yield {
          Some(manifest)
        }
    }
  }

  override def read(fileName: String): Stream[IO, Byte] = {
    def splitChunks(length: Long, chunk: Long): List[Long] = {
      val regularChunks = math.floor(length.toDouble / chunk).toInt
      val lastChunk     = length - regularChunks * chunk
      if (lastChunk == 0) {
        (0 until regularChunks).map(i => chunk).toList
      } else {
        ((0 until regularChunks).map(i => chunk) :+ lastChunk).toList
      }
    }
    def nextChunk(input: IndexInput, chunkSize: Long): IO[Chunk[Byte]] = IO {
      val buffer = new Array[Byte](chunkSize.toInt)
      input.readBytes(buffer, 0, chunkSize.toInt)
      Chunk.byteBuffer(ByteBuffer.wrap(buffer))
    }
    for {
      input <- Stream.bracket(IO(dir.openInput(fileName, IOContext.READ)).handleErrorWith(wrapExceptions))(input =>
        IO(input.close())
      )
      inputSize <- Stream.eval(IO(input.length()))
      _         <- Stream.eval(debug(s"reading file $fileName, size=$inputSize"))
      chunks = splitChunks(inputSize, IO_BUFFER_SIZE)
      byte <- Stream.emits(chunks).evalMap(chunkSize => nextChunk(input, chunkSize)).unchunks
    } yield {
      byte
    }
  }

  override def write(fileName: String, stream: Stream[IO, Byte]): IO[Unit] = {
    Stream
      .bracket(IO(dir.createOutput(fileName, IOContext.DEFAULT)).handleErrorWith(wrapExceptions))(out =>
        IO(out.close())
      )
      .evalTap(_ => debug(s"writing file $fileName"))
      .flatMap(out =>
        stream.chunkN(IO_BUFFER_SIZE.toInt).evalMap(chunk => IO(out.writeBytes(chunk.toByteBuffer.array(), chunk.size)))
      )
      .compile
      .drain
  }

  override def delete(fileName: String): IO[Unit] =
    debug(s"deleting $fileName") *> IO(dir.deleteFile(fileName)).handleErrorWith(wrapExceptions)

  override def close(): IO[Unit] = IO.unit

  private def wrapExceptions(ex: Throwable) = ex match {
    case ex: NoSuchFileException =>
      error(s"IO error: $ex", ex) *> IO.raiseError(FileMissingError(IndexManifest.MANIFEST_FILE_NAME))
    case ex: FileAlreadyExistsException =>
      error(s"IO error: $ex", ex) *> IO.raiseError(FileExistsError(IndexManifest.MANIFEST_FILE_NAME))
    case other => IO.raiseError(ex)
  }
}

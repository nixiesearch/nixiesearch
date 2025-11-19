package ai.nixiesearch.index.store

import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.manifest.IndexManifest.IndexFile
import ai.nixiesearch.index.store.StateClient.StateError
import ai.nixiesearch.index.store.StateClient.StateError.*
import cats.effect.{IO, Resource}
import org.apache.lucene.store.{Directory, IOContext, IndexInput}
import fs2.{Chunk, Stream}
import io.circe.parser.*
import org.apache.lucene.index.{DirectoryReader, SegmentInfos}

import java.nio.ByteBuffer
import java.nio.file.{FileAlreadyExistsException, NoSuchFileException}
import scala.jdk.CollectionConverters.*

case class DirectoryStateClient(dir: Directory, indexName: IndexName) extends StateClient with Logging {
  val IO_BUFFER_SIZE = 16 * 1024L

  private def inputSize(name: String): IO[Long] = IO.blocking {
    val input = dir.openInput(name, IOContext.DEFAULT)
    val size  = input.length()
    input.close()
    size
  }

  override def createManifest(mapping: IndexMapping, seqnum: Long): IO[IndexManifest] = for {
    files <- IO.blocking(
      if (DirectoryReader.indexExists(dir)) SegmentInfos.readLatestCommit(dir).files(true).asScala.toList else Nil
    )
    entries <- Stream
      .emits(files)
      .evalMap(file => inputSize(file).map(size => IndexFile(file, size)))
      .compile
      .toList
  } yield {
    IndexManifest(mapping, entries, seqnum)
  }

  override def readManifest(): IO[Option[IndexManifest]] = {
    IO.blocking(dir.listAll().contains(IndexManifest.MANIFEST_FILE_NAME)).flatMap {
      case false => IO.none
      case true  =>
        for {
          _ <- debug(
            s"existing ${IndexManifest.MANIFEST_FILE_NAME} file found for Lucene directory $dir for index ${indexName.value}"
          )
          manifestBytes <- read(IndexManifest.MANIFEST_FILE_NAME, None).compile.to(Array)
          manifest      <- IO(decode[IndexManifest](new String(manifestBytes))).flatMap {
            case Left(err)    => IO.raiseError(err)
            case Right(value) => IO.pure(value)
          }
          _ <- debug(s"read ${IndexManifest.MANIFEST_FILE_NAME} for index '${manifest.mapping.name}'")
        } yield {
          Some(manifest)
        }
    }
  }

  override def read(fileName: String, sizeHint: Option[Long]): Stream[IO, Byte] = {
    def splitChunks(length: Long, chunk: Long): List[Long] = {
      val regularChunks = math.floor(length.toDouble / chunk).toInt
      val lastChunk     = length - regularChunks * chunk
      if (lastChunk == 0) {
        (0 until regularChunks).map(i => chunk).toList
      } else {
        ((0 until regularChunks).map(i => chunk) :+ lastChunk).toList
      }
    }
    def nextChunk(input: IndexInput, chunkSize: Long): IO[Chunk[Byte]] = IO.blocking {
      val buffer = new Array[Byte](chunkSize.toInt)
      input.readBytes(buffer, 0, chunkSize.toInt)
      Chunk.byteBuffer(ByteBuffer.wrap(buffer))
    }
    for {
      input <- Stream.bracket(IO.blocking(dir.openInput(fileName, IOContext.DEFAULT)).handleErrorWith(wrapExceptions))(
        input => IO.blocking(input.close())
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
    for {
      exists <- IO.blocking(dir.listAll().contains(fileName))
      _      <- IO.whenA(exists)(IO.blocking(dir.deleteFile(fileName)) *> debug(s"overwritten file '$fileName'"))
      _      <- Stream
        .bracket(IO.blocking(dir.createOutput(fileName, IOContext.DEFAULT)).handleErrorWith(wrapExceptions))(out =>
          IO.blocking(out.close())
        )
        .evalTap(_ => debug(s"writing file $fileName"))
        .flatMap(out =>
          stream
            .chunkN(IO_BUFFER_SIZE.toInt)
            .evalMap(chunk => IO.blocking(out.writeBytes(chunk.toArray, chunk.size)))
        )
        .compile
        .drain
    } yield {}
  }

  override def delete(fileName: String): IO[Unit] =
    debug(s"deleting $fileName") *> IO.blocking(dir.deleteFile(fileName)).handleErrorWith(wrapExceptions)

  private def wrapExceptions(ex: Throwable) = ex match {
    case ex: NoSuchFileException =>
      error(s"IO error: $ex", ex) *> IO.raiseError(FileMissingError(IndexManifest.MANIFEST_FILE_NAME))
    case ex: FileAlreadyExistsException =>
      error(s"IO error: $ex", ex) *> IO.raiseError(FileExistsError(IndexManifest.MANIFEST_FILE_NAME))
    case other => IO.raiseError(ex)
  }
}

object DirectoryStateClient extends Logging {
  def create(dir: Directory, indexName: IndexName): Resource[IO, DirectoryStateClient] = for {
    _ <- Resource.eval(debug(s"created DirectoryStateClient for dir=$dir index=${indexName.value}"))
  } yield {
    DirectoryStateClient(dir, indexName)
  }
}

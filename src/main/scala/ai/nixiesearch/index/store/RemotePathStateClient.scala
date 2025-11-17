package ai.nixiesearch.index.store

import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.manifest.IndexManifest.IndexFile
import ai.nixiesearch.index.store.StateClient.StateError.*
import cats.effect.IO
import cats.effect.kernel.Resource
import fs2.io.file.Files
import fs2.io.{readInputStream, writeOutputStream}
import fs2.io.file.Path
import io.circe.parser.*
import cats.syntax.all.*

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.Path as JPath
import fs2.Stream

case class RemotePathStateClient(path: JPath, indexName: IndexName) extends StateClient with Logging {
  val IO_BUFFER_SIZE = 16 * 1024

  override def createManifest(mapping: IndexMapping, seqnum: Long): IO[IndexManifest] = for {
    files <- Files[IO]
      .list(Path.fromNioPath(path))
      .evalMap(file => Files[IO].size(file).map(size => IndexFile(file.fileName.toString, size)))
      .compile
      .toList
  } yield {
    IndexManifest(mapping, files, seqnum)
  }

  override def readManifest(): IO[Option[IndexManifest]] = for {
    _            <- debug(s"reading index manifest '${IndexManifest.MANIFEST_FILE_NAME}'")
    manifestPath <- IO(path.resolve(IndexManifest.MANIFEST_FILE_NAME))
    manifest     <- Files[IO].exists(Path.fromNioPath(manifestPath)).flatMap {
      case false => IO.none
      case true  =>
        for {
          bytes <- readInputStream(IO(new FileInputStream(new File(manifestPath.toUri))), IO_BUFFER_SIZE, true).compile
            .to(Array)
          decoded <- IO(decode[IndexManifest](new String(bytes))).flatMap {
            case Left(error)  => IO.raiseError(error)
            case Right(value) => IO.pure(value)
          }
        } yield {
          Some(decoded)
        }
    }
  } yield {
    manifest
  }

  override def read(fileName: String, sizeHint: Option[Long]): fs2.Stream[IO, Byte] = for {
    filePath <- Stream.eval(IO(path.resolve(fileName)))
    _        <- Stream.eval(debug(s"reading file '$filePath'"))
    exists   <- Stream.eval(Files[IO].exists(Path.fromNioPath(filePath)))
    _        <- Stream.eval(IO.whenA(!exists)(IO.raiseError(FileMissingError(filePath.toString))))
    byte     <- readInputStream(IO(new FileInputStream(new File(filePath.toUri))), IO_BUFFER_SIZE, true)
  } yield {
    byte
  }

  override def write(fileName: String, stream: fs2.Stream[IO, Byte]): IO[Unit] = for {
    filePath <- IO(path.resolve(fileName))
    _        <- debug(s"writing file '$filePath'")
    exists   <- Files[IO].exists(Path.fromNioPath(filePath))
    _        <- IO.whenA(exists)(Files[IO].delete(Path.fromNioPath(filePath)) *> debug(s"overwritten file '$fileName'"))
    _        <- stream
      .chunkN(IO_BUFFER_SIZE)
      .unchunks
      .through(writeOutputStream[IO](IO(new FileOutputStream(new File(filePath.toUri)))))
      .compile
      .drain
  } yield {}

  override def delete(fileName: String): IO[Unit] = for {
    _        <- debug(s"deleting file '$fileName'")
    filePath <- IO(path.resolve(fileName))
    exists   <- Files[IO].exists(Path.fromNioPath(filePath))
    _        <- IO.whenA(!exists)(IO.raiseError(FileMissingError(filePath.toString)))
    _        <- Files[IO].deleteIfExists(Path.fromNioPath(filePath))
  } yield {}

}

object RemotePathStateClient extends Logging {
  def create(path: JPath, indexName: IndexName): Resource[IO, RemotePathStateClient] = for {
    _ <- Resource.eval(debug(s"created RemotePathStateClient for path=$path index=${indexName.value}"))
  } yield {
    RemotePathStateClient(path, indexName)
  }
}

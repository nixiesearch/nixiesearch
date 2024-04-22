package ai.nixiesearch.index.store

import ai.nixiesearch.index.manifest.IndexManifest
import cats.effect.IO
import cats.effect.kernel.Resource
import org.apache.lucene.store.{Directory, FilterDirectory, IOContext}
import io.circe.parser.*

case class NixieDirectory(inner: Directory) extends FilterDirectory(inner) {
  def readManifest(): IO[Option[IndexManifest]] = for {
    bytesOption <- readFileComplete(IndexManifest.MANIFEST_FILE_NAME)
    decoded <- bytesOption match {
      case None => IO.none
      case Some(bytes) =>
        IO(decode[IndexManifest](new String(bytes))).flatMap {
          case Left(error)  => IO.raiseError(error)
          case Right(value) => IO.pure(Some(value))
        }
    }
  } yield {
    decoded
  }

  def readFileComplete(name: String): IO[Option[Array[Byte]]] = for {
    exists <- IO(listAll().contains(name))
    result <- exists match {
      case false => IO.pure(None)
      case true =>
        for {
          len    <- IO(fileLength(name)).map(_.toInt)
          buffer <- IO(new Array[Byte](len))
          _ <- Resource
            .make(IO(inner.openInput(name, IOContext.READ)))(input => IO(input.close()))
            .use(input => IO(input.readBytes(buffer, 0, len)))
        } yield {
          Some(buffer)
        }
    }

  } yield {
    result
  }
}

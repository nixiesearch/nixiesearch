package ai.nixiesearch.util.source
import ai.nixiesearch.config.URL
import ai.nixiesearch.config.URL.{LocalURL, S3URL}
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.util.S3Client
import cats.effect.IO
import fs2.Stream
import fs2.io.file.{Files, Path as FPath}
import fs2.io.readInputStream

import java.io.FileInputStream

object URLReader extends SourceReader {
  override def bytes(url: URL, recursive: Boolean = false): Stream[IO, Byte] = recursive match {
    case false =>
      url match {
        case url: URL.LocalURL => FileReader.bytes(url)
        case URL.HttpURL(path) => Stream.raiseError(BackendError("http not yet supported"))
        case url: S3URL        => S3Reader.bytes(url)
      }
    case true =>
      url match {
        case url: URL.LocalURL => FileReader.bytesRecursive(url)
        case URL.HttpURL(path) => Stream.raiseError(BackendError("http not yet supported"))
        case url: S3URL        => S3Reader.bytesRecursive(url)
      }
  }

  object FileReader {
    def bytesRecursive(dir: LocalURL): Stream[IO, Byte] = for {
      file <- Files[IO].list(FPath.fromNioPath(dir.path))
      byte <- readInputStream[IO](IO(new FileInputStream(file.toNioPath.toFile)), 1024)
    } yield {
      byte
    }

    def bytes(url: LocalURL): Stream[IO, Byte] =
      readInputStream[IO](IO(new FileInputStream(url.path.toFile)), 1024)
  }

  object S3Reader {
    def bytesRecursive(url: S3URL): Stream[IO, Byte] = for {
      client <- Stream.resource(S3Client.create(url.region.getOrElse("us-east-1"), url.endpoint))
      file   <- client.listObjects(url.bucket, url.prefix)
      stream <- Stream.eval(client.getObject(url.bucket, url.prefix + file.name))
      byte   <- stream
    } yield {
      byte
    }

    def bytes(url: S3URL): Stream[IO, Byte] = for {
      client <- Stream.resource(S3Client.create(url.region.getOrElse("us-east-1"), url.endpoint))
      stream <- Stream.eval(client.getObject(url.bucket, url.prefix))
      byte   <- stream
    } yield {
      byte
    }
  }

}

package ai.nixiesearch.util.source

import ai.nixiesearch.config.URL
import ai.nixiesearch.config.URL.{HttpURL, LocalURL, S3URL}
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.util.S3Client
import cats.effect.IO
import de.lhns.fs2.compress.{Bzip2Decompressor, GzipDecompressor, ZstdDecompressor}
import fs2.Stream
import fs2.io.file.{Files, Path as FPath}
import fs2.io.readInputStream
import org.http4s.ember.client.EmberClientBuilder

import java.io.FileInputStream

object URLReader extends SourceReader with Logging {
  override def bytes(url: URL, recursive: Boolean = false): Stream[IO, Byte] = recursive match {
    case false =>
      url match {
        case url: URL.LocalURL => FileReader.bytes(url)
        case url: URL.HttpURL  => HTTPReader.bytes(url)
        case url: S3URL        => S3Reader.bytes(url)
      }
    case true =>
      url match {
        case url: URL.LocalURL => FileReader.bytesRecursive(url)
        case URL.HttpURL(path) => Stream.raiseError(BackendError("recursive gets over http are not yet supported"))
        case url: S3URL        => S3Reader.bytesRecursive(url)
      }
  }

  object FileReader {
    def bytesRecursive(dir: LocalURL): Stream[IO, Byte] = for {
      _    <- Stream.eval(info(s"recursively reading file directory ${dir.path}"))
      file <- Files[IO].list(FPath.fromNioPath(dir.path))
      _    <- Stream.eval(info(s"reading file ${file}"))
      byte <- readInputStream[IO](IO(new FileInputStream(file.toNioPath.toFile)), 1024)
    } yield {
      byte
    }

    def bytes(url: LocalURL): Stream[IO, Byte] = for {
      _    <- Stream.eval(info(s"reading file ${url.path}"))
      byte <- readInputStream[IO](IO(new FileInputStream(url.path.toFile)), 1024)

    } yield {
      byte
    }
  }

  object S3Reader {
    def bytesRecursive(url: S3URL): Stream[IO, Byte] = for {
      _      <- Stream.eval(info(s"recursively reading S3 directory ${url}"))
      client <- Stream.resource(S3Client.create(url.region.getOrElse("us-east-1"), url.endpoint))
      file   <- client.listObjects(url.bucket, url.prefix)
      _      <- Stream.eval(info(s"reading S3 file ${file}"))
      stream <- Stream.eval(client.getObject(url.bucket, url.prefix + file.name))
      byte   <- stream
    } yield {
      byte
    }

    def bytes(url: S3URL): Stream[IO, Byte] = for {
      _      <- Stream.eval(info(s"reading S3 file ${url}"))
      client <- Stream.resource(S3Client.create(url.region.getOrElse("us-east-1"), url.endpoint))
      stream <- Stream.eval(client.getObject(url.bucket, url.prefix))
      byte   <- stream
    } yield {
      byte
    }
  }

  object HTTPReader {
    def bytes(url: HttpURL): Stream[IO, Byte] = for {
      _            <- Stream.eval(info(s"reading HTTP file ${url.path}"))
      client       <- Stream.resource(EmberClientBuilder.default[IO].build)
      responseBody <- Stream.eval(client.get(url.path)(response => IO(response.body)))
      byte         <- responseBody
    } yield {
      byte
    }

  }

}

package ai.nixiesearch.util.source

import ai.nixiesearch.config.URL
import ai.nixiesearch.config.URL.{HttpURL, LocalURL, S3URL}
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.util.S3ClientOps
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
      byte <- maybeDecompress(
        readInputStream[IO](IO(new FileInputStream(file.toNioPath.toFile)), 1024),
        file.fileName.toString
      )
    } yield {
      byte
    }

    def bytes(url: LocalURL): Stream[IO, Byte] = for {
      _ <- Stream.eval(info(s"reading file ${url.path}"))
      byte <- maybeDecompress(
        readInputStream[IO](IO(new FileInputStream(url.path.toFile)), 1024),
        url.path.getFileName.toString
      )
    } yield {
      byte
    }
  }

  object S3Reader {
    def bytesRecursive(url: S3URL): Stream[IO, Byte] = for {
      _      <- Stream.eval(info(s"recursively reading S3 directory ${url}"))
      client <- Stream.resource(S3ClientOps.create(url.region.getOrElse("us-east-1"), url.endpoint))
      file   <- client.listObjects(url.bucket, url.prefix)
      _      <- Stream.eval(info(s"reading S3 file ${file}"))
      stream <- Stream.eval(client.getObject(url.bucket, url.prefix + file.name))
      byte   <- maybeDecompress(stream, file.name)
    } yield {
      byte
    }

    def bytes(url: S3URL): Stream[IO, Byte] = for {
      _      <- Stream.eval(info(s"reading S3 file ${url}"))
      client <- Stream.resource(S3ClientOps.create(url.region.getOrElse("us-east-1"), url.endpoint))
      stream <- Stream.eval(client.getObject(url.bucket, url.prefix))
      byte   <- maybeDecompress(stream, url.prefix)
    } yield {
      byte
    }
  }

  object HTTPReader {
    def bytes(url: HttpURL): Stream[IO, Byte] = for {
      _            <- Stream.eval(info(s"reading HTTP file ${url.path}"))
      client       <- Stream.resource(EmberClientBuilder.default[IO].build)
      responseBody <- Stream.eval(client.get(url.path)(response => IO(response.body)))
      byte         <- maybeDecompress(responseBody, url.path.fragment.getOrElse(""))
    } yield {
      byte
    }

  }

  given gzip: GzipDecompressor[IO]   = GzipDecompressor.make()
  given bzip2: Bzip2Decompressor[IO] = Bzip2Decompressor.make()
  given zstd: ZstdDecompressor[IO]   = ZstdDecompressor.make()

  def maybeDecompress(source: Stream[IO, Byte], fileName: String): Stream[IO, Byte] = fileName match {
    case f if f.endsWith(".gz") =>
      for {
        _    <- Stream.eval(info(s"decompressing gzipped file $fileName"))
        byte <- source.through(GzipDecompressor[IO].decompress)
      } yield byte
    case f if f.endsWith(".bzip2") =>
      for {
        _    <- Stream.eval(info(s"decompressing bzipped file $fileName"))
        byte <- source.through(Bzip2Decompressor[IO].decompress)
      } yield byte
    case f if f.endsWith(".zst") =>
      for {
        _    <- Stream.eval(info(s"decompressing zstd file $fileName"))
        byte <- source.through(ZstdDecompressor[IO].decompress)
      } yield byte
    case _ => source
  }

}

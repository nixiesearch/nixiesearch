package ai.nixiesearch.index.manifest

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.manifest.IndexManifest.IndexFile
import cats.effect.{IO, Resource}
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}
import io.circe.syntax.*
import io.circe.parser.*
import org.apache.lucene.store.{Directory, IOContext}
import fs2.Stream

case class IndexManifest(mapping: IndexMapping, files: List[IndexFile], seqnum: Long)

object IndexManifest extends Logging {
  val MANIFEST_FILE_NAME = "index.json"

  import IndexMapping.json.given

  given indexManifestEncoder: Encoder[IndexManifest] = deriveEncoder
  given indexManifestDecoder: Decoder[IndexManifest] = deriveDecoder

  given indexFileEncoder: Encoder[IndexFile] = deriveEncoder
  given indexFileDecoder: Decoder[IndexFile] = deriveDecoder

  case class IndexFile(name: String, size: Long)

  def create(dir: Directory, mapping: IndexMapping, seqnum: Long): IO[IndexManifest] = for {
    files <- Stream
      .emits(dir.listAll().toList)
      .evalMap(name => IO(dir.fileLength(name)).map(size => IndexFile(name, size)))
      .compile
      .toList
    _ <- debug(s"creating manifest for index '${mapping.name}'")
  } yield {
    IndexManifest(mapping, files, seqnum)
  }

  def read(dir: Directory): IO[Option[IndexManifest]] = for {
    bytesOption <- readFileComplete(dir, IndexManifest.MANIFEST_FILE_NAME)
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

  def write(dir: Directory, manifest: IndexManifest): IO[Unit] = for {
    jsonBytes <- IO(manifest.asJson.spaces2.getBytes)
    _ <- IO(dir.listAll().contains(IndexManifest.MANIFEST_FILE_NAME)).flatMap {
      case false => IO.unit
      case true =>
        debug(s"replacing existing ${IndexManifest.MANIFEST_FILE_NAME} file") *> IO(
          dir.deleteFile(IndexManifest.MANIFEST_FILE_NAME)
        )
    }
    _ <- Resource
      .make(IO(dir.createOutput(IndexManifest.MANIFEST_FILE_NAME, IOContext.DEFAULT)))(out => IO(out.close()))
      .use(io => IO(io.writeBytes(jsonBytes, jsonBytes.length)))
    _ <- debug(s"wrote manifest for index '${manifest.mapping.name} seqnum=${manifest.seqnum}'")
  } yield {}

  private def readFileComplete(dir: Directory, name: String): IO[Option[Array[Byte]]] = for {
    exists <- IO(dir.listAll().contains(name))
    result <- exists match {
      case false => IO.pure(None)
      case true =>
        for {
          len    <- IO(dir.fileLength(name)).map(_.toInt)
          buffer <- IO(new Array[Byte](len))
          _ <- Resource
            .make(IO(dir.openInput(name, IOContext.READ)))(input => IO(input.close()))
            .use(input => IO(input.readBytes(buffer, 0, len)))
        } yield {
          Some(buffer)
        }
    }

  } yield {
    result
  }

}

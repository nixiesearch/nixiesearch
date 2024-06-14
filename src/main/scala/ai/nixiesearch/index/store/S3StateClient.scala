package ai.nixiesearch.index.store
import ai.nixiesearch.config.StoreConfig.BlockStoreLocation.S3Location
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.manifest.IndexManifest.IndexFile
import ai.nixiesearch.index.store.StateClient.StateError
import ai.nixiesearch.index.store.StateClient.StateError.FileExistsError
import ai.nixiesearch.util.S3Client
import cats.effect.{IO, Resource}
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import fs2.{Chunk, Stream}
import software.amazon.awssdk.services.s3.model.{
  CompleteMultipartUploadRequest,
  CompletedMultipartUpload,
  CompletedPart,
  CreateMultipartUploadRequest,
  DeleteObjectRequest,
  GetObjectRequest,
  GetObjectResponse,
  HeadObjectRequest,
  HeadObjectResponse,
  ListObjectsV2Request,
  NoSuchKeyException,
  PutObjectRequest,
  UploadPartRequest
}
import fs2.interop.reactivestreams.*
import software.amazon.awssdk.core.async.{AsyncRequestBody, AsyncResponseTransformer, SdkPublisher}
import io.circe.parser.*

import scala.jdk.CollectionConverters.*
import java.net.URI
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletableFuture
import scala.compiletime.uninitialized

case class S3StateClient(s3: S3Client, conf: S3Location, indexName: IndexName) extends StateClient with Logging {
  val IO_BUFFER_SIZE = 5 * 1024 * 1024

  override def createManifest(mapping: IndexMapping, seqnum: Long): IO[IndexManifest] = for {
    path  <- IO(s"${conf.prefix}/${indexName.value}/")
    _     <- debug(s"Creating manifest for index s3://${conf.bucket}/$path")
    files <- s3.listObjects(conf.bucket, path).compile.toList
    now   <- IO(Instant.now().toEpochMilli)
  } yield {
    IndexManifest(
      mapping = mapping,
      files = files.map(f => IndexFile(f.name, f.lastModified)),
      seqnum = seqnum
    )
  }
  override def readManifest(): IO[Option[IndexManifest]] =
    for {
      path <- IO(s"${conf.prefix}/${indexName.value}/${IndexManifest.MANIFEST_FILE_NAME}")
      _    <- debug(s"Reading s3://${conf.bucket}/$path")
      manifest <- s3.getObject(conf.bucket, path).attempt.flatMap {
        case Left(e: NoSuchKeyException) => IO.none
        case Left(error)                 => wrapException(IndexManifest.MANIFEST_FILE_NAME)(error)
        case Right(stream) =>
          for {
            bytes <- stream.compile.to(Array)
            decoded <- IO(decode[IndexManifest](new String(bytes))).flatMap {
              case Left(err)    => IO.raiseError(err)
              case Right(value) => IO.pure(value)
            }
          } yield {
            Some(decoded)
          }
      }
    } yield {
      manifest
    }

  override def read(fileName: String): Stream[IO, Byte] = for {
    path         <- Stream.emit(s"${conf.prefix}/${indexName.value}/$fileName")
    _            <- Stream.eval(debug(s"Reading s3://${conf.bucket}/$path"))
    objectStream <- Stream.eval(s3.getObject(conf.bucket, path).handleErrorWith(wrapException(fileName)))
    byte         <- objectStream
  } yield {
    byte
  }

  override def write(fileName: String, stream: Stream[IO, Byte]): IO[Unit] = for {
    path <- IO(s"${conf.prefix}/${indexName.value}/$fileName")
    _    <- debug(s"writing s3://${conf.bucket}/$path")
    _    <- s3.multipartUpload(conf.bucket, path, stream)
  } yield {}

  override def delete(fileName: String): IO[Unit] = for {
    path    <- IO(s"${conf.prefix}/${indexName.value}/$fileName")
    _       <- debug(s"deleting s3://${conf.bucket}/$path")
    head    <- s3.head(conf.bucket, path).handleErrorWith(wrapException(fileName))
    request <- IO(DeleteObjectRequest.builder().bucket(conf.bucket).key(path).build())
    _       <- IO.fromCompletableFuture(IO(s3.client.deleteObject(request))).handleErrorWith(wrapException(fileName))
  } yield {}

  private def wrapException[T](fileName: String)(ex: Throwable): IO[T] = ex match {
    case e: NoSuchKeyException => IO.raiseError(StateError.FileMissingError(fileName))
    case other                 => IO.raiseError(other)
  }

}

object S3StateClient extends Logging {

  def create(conf: S3Location, indexName: IndexName): Resource[IO, S3StateClient] = for {
    _      <- Resource.eval(debug(s"creating S3StateClient for conf=$conf index=${indexName.value}"))
    client <- S3Client.create(conf.region.getOrElse("us-east-1"), conf.endpoint)
  } yield {
    S3StateClient(client, conf, indexName)
  }
}

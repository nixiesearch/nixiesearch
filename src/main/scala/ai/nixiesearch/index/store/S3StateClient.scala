package ai.nixiesearch.index.store
import ai.nixiesearch.config.StoreConfig.BlockStoreLocation.S3Location
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.store.S3StateClient.S3GetObjectResponseStream
import ai.nixiesearch.index.store.StateClient.StateError
import ai.nixiesearch.index.store.StateClient.StateError.FileExistsError
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

import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

case class S3StateClient(client: S3AsyncClient, conf: S3Location, indexName: String) extends StateClient with Logging {
  val IO_BUFFER_SIZE = 1024 * 1024

  override def createManifest(mapping: IndexMapping, seqnum: Long): IO[IndexManifest] = ???
  override def readManifest(): IO[Option[IndexManifest]] = {
    for {
      path <- IO(s"${conf.prefix}/$indexName/${IndexManifest.MANIFEST_FILE_NAME}")
      _    <- debug(s"Reading s3://${conf.bucket}/$path")
      manifest <- getObjectRequest(path).compile
        .to(Array)
        .map(b => Some(b))
        .handleErrorWith {
          case e: NoSuchKeyException => IO.none
          case other                 => IO.raiseError(other)
        }
        .flatMap {
          case None => IO.none
          case Some(bytes) =>
            IO(decode[IndexManifest](new String(bytes))).flatMap {
              case Left(err)    => IO.raiseError(err)
              case Right(value) => IO.some(value)
            }
        }
    } yield {
      manifest
    }
  }

  override def read(fileName: String): Stream[IO, Byte] = for {
    path <- Stream.emit(s"${conf.prefix}/$indexName/$fileName")
    _    <- Stream.eval(debug(s"Reading s3://${conf.bucket}/$path"))
    byte <- getObjectRequest(path)
  } yield {
    byte
  }

  override def write(fileName: String, stream: Stream[IO, Byte]): IO[Unit] = for {
    path <- IO(s"${conf.prefix}/$indexName/$fileName")
    _    <- debug(s"writing s3://${conf.bucket}/$path")
    head <- headRequest(path)
      .flatMap(headResponse => info(s"$headResponse") *> IO.raiseError(FileExistsError(fileName)))
      .handleErrorWith {
        case ex: FileExistsError    => IO.raiseError(ex)
        case ex: NoSuchKeyException => debug("file is not present on S3, creating multipart upload") *> IO.unit
        case ex                     => wrapException(fileName)(ex)
      }

    request <- IO(CreateMultipartUploadRequest.builder().bucket(conf.bucket).key(path).build())
    mpart   <- IO.fromCompletableFuture(IO(client.createMultipartUpload(request)))
    completedParts <- stream
      .chunkN(IO_BUFFER_SIZE)
      .zipWithIndex
      .evalMap { case (chunk, index) =>
        for {
          request <- IO(
            UploadPartRequest
              .builder()
              .bucket(conf.bucket)
              .key(path)
              .uploadId(mpart.uploadId())
              .partNumber(index.toInt + 1)
              .build()
          )
          response <- IO.fromCompletableFuture(
            IO(client.uploadPart(request, AsyncRequestBody.fromByteBuffer(chunk.toByteBuffer)))
          )
        } yield {
          CompletedPart.builder().partNumber(index.toInt + 1).eTag(response.eTag()).build()
        }
      }
      .compile
      .toList
    request <- IO(
      CompleteMultipartUploadRequest
        .builder()
        .bucket(conf.bucket)
        .key(path)
        .uploadId(mpart.uploadId())
        .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts: _*).build())
        .build()
    )
    _ <- IO.fromCompletableFuture(IO(client.completeMultipartUpload(request)))
  } yield {}

  override def delete(fileName: String): IO[Unit] = for {
    path    <- IO(s"${conf.prefix}/$indexName/$fileName")
    _       <- debug(s"deleting s3://${conf.bucket}/$path")
    head    <- headRequest(path).handleErrorWith(wrapException(fileName))
    request <- IO(DeleteObjectRequest.builder().bucket(conf.bucket).key(path).build())
    _       <- IO.fromCompletableFuture(IO(client.deleteObject(request))).handleErrorWith(wrapException(fileName))
  } yield {}

  override def close(): IO[Unit] = for {
    _ <- info(s"closing S3 client")
    _ <- IO(client.close())
  } yield {}

  private def wrapException[T](fileName: String)(ex: Throwable): IO[T] = ex match {
    case e: NoSuchKeyException => IO.raiseError(StateError.FileMissingError(fileName))
    case other                 => IO.raiseError(other)
  }

  private def headRequest(path: String): IO[HeadObjectResponse] = for {
    request  <- IO(HeadObjectRequest.builder().bucket(conf.bucket).key(path).build())
    response <- IO.fromCompletableFuture(IO(client.headObject(request)))
  } yield {
    response
  }

  private def getObjectRequest(path: String): Stream[IO, Byte] = for {
    request <- Stream.eval(IO(GetObjectRequest.builder().bucket(conf.bucket).key(path).build()))
    byte    <- Stream.eval(IO.fromCompletableFuture(IO(client.getObject(request, S3GetObjectResponseStream())))).flatten
  } yield {
    byte
  }
}

object S3StateClient {
  class S3GetObjectResponseStream[T]()
      extends AsyncResponseTransformer[GetObjectResponse, Stream[IO, Byte]]
      with Logging {
    var cf: CompletableFuture[Stream[IO, Byte]] = _

    override def prepare(): CompletableFuture[Stream[IO, Byte]] = {
      cf = new CompletableFuture[Stream[IO, Byte]]()
      cf
    }

    override def onResponse(response: GetObjectResponse): Unit = {
      logger.debug(s"S3 response: $response")
    }

    override def onStream(publisher: SdkPublisher[ByteBuffer]): Unit = {
      logger.debug("subscribed to S3 GetObject data stream")
      val stream = fromPublisher[IO, ByteBuffer](publisher, 1).flatMap(bb => Stream.chunk(Chunk.byteBuffer(bb)))
      cf.complete(stream)
    }

    override def exceptionOccurred(error: Throwable): Unit = {
      logger.error("AWS SDK errorq", error)
      cf.completeExceptionally(error)
    }
  }

  def create(conf: S3Location, indexName: String): IO[S3StateClient] = for {
    creds <- IO(DefaultCredentialsProvider.create())
    clientBuilder <- IO(
      S3AsyncClient
        .builder()
        .region(Region.of(conf.region.getOrElse("us-east-1")))
        .credentialsProvider(creds)
        .forcePathStyle(conf.endpoint.isDefined)
    )
    client = conf.endpoint match {
      case Some(endpoint) => clientBuilder.endpointOverride(URI.create(endpoint)).build()
      case None           => clientBuilder.build()
    }
  } yield {
    S3StateClient(client, conf, indexName)
  }
}

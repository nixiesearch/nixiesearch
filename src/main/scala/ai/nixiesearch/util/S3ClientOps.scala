package ai.nixiesearch.util

import ai.nixiesearch.core.Logging
import ai.nixiesearch.util.S3ClientOps.{S3File, S3GetObjectResponseStream}
import cats.effect.{IO, Resource}
import software.amazon.awssdk.auth.credentials.{
  AnonymousCredentialsProvider,
  AwsCredentialsProvider,
  AwsCredentialsProviderChain,
  DefaultCredentialsProvider
}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import fs2.{Chunk, Stream}
import fs2.interop.reactivestreams.fromPublisher
import software.amazon.awssdk.auth.credentials.internal.LazyAwsCredentialsProvider
import software.amazon.awssdk.core.async.{AsyncRequestBody, AsyncResponseTransformer, SdkPublisher}
import software.amazon.awssdk.services.s3.model.{
  CompleteMultipartUploadRequest,
  CompletedMultipartUpload,
  CompletedPart,
  CreateMultipartUploadRequest,
  GetObjectRequest,
  GetObjectResponse,
  HeadObjectRequest,
  HeadObjectResponse,
  ListObjectsV2Request,
  UploadPartRequest
}

import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

case class S3ClientOps(client: S3AsyncClient) {
  val IO_BUFFER_SIZE = 5 * 1024 * 1024

  def head(bucket: String, path: String): IO[HeadObjectResponse] = for {
    request  <- IO(HeadObjectRequest.builder().bucket(bucket).key(path).build())
    response <- IO.fromCompletableFuture(IO(client.headObject(request)))
  } yield {
    response
  }

  def getObject(bucket: String, path: String): IO[Stream[IO, Byte]] = for {
    request <- IO(GetObjectRequest.builder().bucket(bucket).key(path).build())
    stream  <- IO.fromCompletableFuture(IO(client.getObject(request, S3GetObjectResponseStream())))
  } yield {
    stream
  }

  def listObjects(bucket: String, path: String): Stream[IO, S3File] = for {
    requestBuilder <- Stream.eval(IO(ListObjectsV2Request.builder().bucket(bucket).prefix(path)))
    files <- Stream
      .unfoldLoopEval(requestBuilder.build())(request =>
        for {
          response <- IO.fromCompletableFuture(IO(client.listObjectsV2(request)))
        } yield {
          val files = response
            .contents()
            .asScala
            .toList
            .map(obj => S3File(obj.key().replace(path, ""), obj.lastModified().toEpochMilli, obj.size()))
          if (response.isTruncated) {
            (files, Some(requestBuilder.continuationToken(response.nextContinuationToken()).build()))
          } else {
            (files, None)
          }
        }
      )
      .map(batch => Chunk.from(batch))
      .unchunks
  } yield {
    files
  }

  def multipartUpload(bucket: String, path: String, stream: Stream[IO, Byte]) = for {
    request <- IO(CreateMultipartUploadRequest.builder().bucket(bucket).key(path).build())
    mpart   <- IO.fromCompletableFuture(IO(client.createMultipartUpload(request)))
    completedParts <- stream
      .chunkN(IO_BUFFER_SIZE)
      .zipWithIndex
      .evalMap { case (chunk, index) =>
        for {
          request <- IO(
            UploadPartRequest
              .builder()
              .bucket(bucket)
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
        .bucket(bucket)
        .key(path)
        .uploadId(mpart.uploadId())
        .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts*).build())
        .build()
    )
    response <- IO.fromCompletableFuture(IO(client.completeMultipartUpload(request)))
  } yield {
    response
  }

}

object S3ClientOps {
  case class S3File(name: String, lastModified: Long, size: Long)

  class S3GetObjectResponseStream[T]()
      extends AsyncResponseTransformer[GetObjectResponse, Stream[IO, Byte]]
      with Logging {
    var cf: CompletableFuture[Stream[IO, Byte]] = uninitialized

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
      cf.completeExceptionally(error)
    }
  }

  def createCredentialsProvider(): AwsCredentialsProvider = {
    val chain = AwsCredentialsProviderChain
      .builder()
      .addCredentialsProvider(DefaultCredentialsProvider.create())
      .addCredentialsProvider(AnonymousCredentialsProvider.create())
      .build()
    LazyAwsCredentialsProvider.create(() => chain)
  }

  def create(region: String, endpoint: Option[String]): Resource[IO, S3ClientOps] = for {
    creds <- Resource.eval(IO(createCredentialsProvider()))
    clientBuilder <- Resource.eval(
      IO(
        S3AsyncClient
          .builder()
          .region(Region.of(region))
          .credentialsProvider(creds)
          .forcePathStyle(endpoint.isDefined)
      )
    )
    client <- endpoint match {
      case Some(endpoint) =>
        Resource.make(IO(clientBuilder.endpointOverride(URI.create(endpoint)).build()))(c => IO(c.close()))
      case None => Resource.make(IO(clientBuilder.build()))(c => IO(c.close()))
    }

  } yield {
    S3ClientOps(client)
  }
}

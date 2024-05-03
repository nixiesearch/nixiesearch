package ai.nixiesearch.index.store.s3

import ai.nixiesearch.core.Logging
import cats.effect.IO
import software.amazon.awssdk.core.async.{AsyncResponseTransformer, SdkPublisher}
import fs2.{Chunk, Stream}
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import fs2.interop.reactivestreams.*

import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

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
    logger.error("oops", error)
    cf.completeExceptionally(error)
  }
}

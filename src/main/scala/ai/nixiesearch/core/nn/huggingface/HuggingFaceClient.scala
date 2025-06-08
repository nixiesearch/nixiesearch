package ai.nixiesearch.core.nn.huggingface

import ai.nixiesearch.core.Error.*
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.huggingface.HuggingFaceClient.ModelResponse
import ai.nixiesearch.core.nn.huggingface.HuggingFaceClient.ModelResponse.Sibling
import ai.nixiesearch.core.nn.huggingface.ModelFileCache.CacheKey
import ai.nixiesearch.core.{Logging, PrintProgress}
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fs2.{Chunk, Stream}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.*
import io.circe.syntax.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.{EntityDecoder, Request, Uri}
import org.typelevel.ci.CIString

import java.nio.ByteBuffer
import java.nio.file.{Files, Path}
import scala.concurrent.duration.*

case class HuggingFaceClient(client: Client[IO], endpoint: Uri, cache: ModelFileCache) extends Logging {
  val MODEL_FILE                                                      = "model_card.json"
  implicit val modelResponseDecoder: EntityDecoder[IO, ModelResponse] = jsonOf[IO, ModelResponse]

  def model(handle: HuggingFaceHandle): IO[ModelResponse] =
    cache.getIfExists(CacheKey(handle.ns, handle.name, MODEL_FILE)).flatMap {
      case Some(path) =>
        info(s"found cached $MODEL_FILE card") *> IO.fromEither(decode[ModelResponse](Files.readString(path)))
      case None =>
        for {
          request  <- IO(Request[IO](uri = endpoint / "api" / "models" / handle.ns / handle.name))
          _        <- info(s"sending HuggingFace API request $request")
          response <- client.expect[ModelResponse](request)
          _        <- cache.put(
            key = CacheKey(handle.ns, handle.name, MODEL_FILE),
            bytes = Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(response.asJson.spaces2.getBytes())))
          )
        } yield {
          response
        }
    }

  def modelFile(handle: HuggingFaceHandle, fileName: String): Stream[IO, Byte] = {
    get(endpoint / handle.ns / handle.name / "resolve" / "main" / fileName)
  }

  def get(uri: Uri): Stream[IO, Byte] = for {
    response <- client.stream(Request[IO](uri = uri))
    _        <- Stream.eval(info(s"sending HuggingFace API request for a file $uri"))
    byte     <- response.status.code match {
      case 200 => response.entity.body.through(PrintProgress.bytes)
      case 302 =>
        response.headers.get(CIString("Location")) match {
          case Some(locations) =>
            Uri.fromString(locations.head.value) match {
              case Left(value) => Stream.raiseError[IO](BackendError(value.message))
              case Right(uri)  => Stream.eval(info(s"redirect to $uri")) *> get(uri)
            }
          case None => Stream.raiseError[IO](BackendError("No location header"))
        }
      case other => Stream.raiseError[IO](BackendError(s"HTTP code $other"))
    }
  } yield {
    byte
  }

  def getCached(handle: HuggingFaceHandle, file: String): IO[Path] = for {
    cached <- cache.getIfExists(CacheKey(handle.ns, handle.name, file))
    bytes  <- cached match {
      case Some(path) =>
        info(s"found cached $path file for requested ${handle.ns}/${handle.name}/$file") *> IO.pure(path)
      case None =>
        for {
          _    <- info(s"not found $file in cache")
          _    <- cache.put(CacheKey(handle.ns, handle.name, file), modelFile(handle, file))
          path <- cache.get(CacheKey(handle.ns, handle.name, file))
        } yield {
          path
        }
    }
  } yield {
    bytes
  }
}

object HuggingFaceClient extends Logging {
  val HUGGINGFACE_API_ENDPOINT = "https://huggingface.co"

  case class ModelResponse(id: String, siblings: List[Sibling])
  object ModelResponse {
    case class Sibling(rfilename: String)
  }

  given modelSiblingCodec: Codec[Sibling]        = deriveCodec
  given modelResponseCodec: Codec[ModelResponse] = deriveCodec

  def create(cache: ModelFileCache, endpoint: String = HUGGINGFACE_API_ENDPOINT): Resource[IO, HuggingFaceClient] =
    for {
      uri    <- Resource.eval(IO.fromEither(Uri.fromString(endpoint)))
      client <- EmberClientBuilder
        .default[IO]
        .withTimeout(120.second)
        .build
    } yield {
      HuggingFaceClient(client, uri, cache)
    }

}

package ai.nixiesearch.core.nn.model

import ai.nixiesearch.core.{Logging, PrintProgress}
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.model.HuggingFaceClient.ModelResponse
import ai.nixiesearch.core.nn.model.HuggingFaceClient.ModelResponse.Sibling
import cats.effect.IO
import cats.effect.kernel.Resource
import fs2.Stream
import io.circe.Codec
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder}
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.{EntityDecoder, Request, Uri}
import org.typelevel.ci.CIString
import ai.nixiesearch.core.Error.*
import java.io.{ByteArrayOutputStream, File}
import scala.concurrent.duration.*

case class HuggingFaceClient(client: Client[IO], endpoint: Uri, cache: ModelFileCache) extends Logging {

  implicit val modelResponseDecoder: EntityDecoder[IO, ModelResponse] = jsonOf[IO, ModelResponse]

  def model(handle: HuggingFaceHandle) = for {
    request  <- IO(Request[IO](uri = endpoint / "api" / "models" / handle.ns / handle.name))
    _        <- info(s"sending HuggingFace API request $request")
    response <- client.expect[ModelResponse](request)
  } yield {
    response
  }

  def modelFile(handle: HuggingFaceHandle, fileName: String): IO[Array[Byte]] = {
    get(endpoint / handle.ns / handle.name / "resolve" / "main" / fileName)
  }

  def get(uri: Uri): IO[Array[Byte]] =
    client
      .stream(Request[IO](uri = uri))
      .evalTap(_ => info(s"sending HuggingFace API request for a file $uri"))
      .evalMap(response =>
        response.status.code match {
          case 200 =>
            info("HuggingFace API: HTTP 200") *> response.entity.body
              .through(PrintProgress.bytes)
              .compile
              .foldChunks(new ByteArrayOutputStream())((acc, c) => {
                acc.writeBytes(c.toArray)
                acc
              })
              .map(_.toByteArray)
          case 302 =>
            response.headers.get(CIString("Location")) match {
              case Some(locations) =>
                Uri.fromString(locations.head.value) match {
                  case Left(value) => IO.raiseError(value)
                  case Right(uri)  => info("302 redirect") *> get(uri)
                }
              case None => IO.raiseError(BackendError(s"Got 302 redirect without location"))
            }
          case other => IO.raiseError(BackendError(s"HTTP code $other"))
        }
      )
      .compile
      .fold(new ByteArrayOutputStream())((acc, c) => {
        acc.writeBytes(c)
        acc
      })
      .map(_.toByteArray)

  def getCached(handle: HuggingFaceHandle, file: String): IO[Array[Byte]] = for {
    modelDirName <- IO(handle.asList.mkString(File.separator))
    bytes <- cache.getIfExists(modelDirName, file).flatMap {
      case Some(bytes) => info(s"found $file in cache") *> IO.pure(bytes)
      case None        => modelFile(handle, file).flatTap(bytes => cache.put(modelDirName, file, bytes))
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
      uri <- Resource.eval(IO.fromEither(Uri.fromString(endpoint)))
      client <- EmberClientBuilder
        .default[IO]
        .withTimeout(120.second)
        .build
    } yield {
      HuggingFaceClient(client, uri, cache)
    }

}

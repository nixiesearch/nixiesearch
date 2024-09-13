package ai.nixiesearch.api

import ai.nixiesearch.api.InferenceRoute.{
  CompletionFrame,
  CompletionRequest,
  CompletionResponse,
  EmbeddingInferenceRequest,
  EmbeddingInferenceResponse,
  EmbeddingOutput,
  PromptType
}
import ai.nixiesearch.api.InferenceRoute.PromptType.{Document, Query, Raw}
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.index.Models
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.util.{DurationStream, StreamMark}
import cats.effect.IO
import io.circe.{Codec, Decoder, Encoder, Json}
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes, Response}
import org.http4s.dsl.io.*
import io.circe.generic.semiauto.*
import org.http4s.circe.*
import fs2.Stream

import scala.util.{Failure, Success}

class InferenceRoute(models: Models) extends Route with Logging {
  override val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> Root / "inference" / "embedding" / modelName =>
      for {
        request  <- req.as[EmbeddingInferenceRequest]
        response <- embed(request, ModelRef(modelName))
        ok       <- Ok(response)
      } yield {
        ok
      }
    case req @ POST -> Root / "inference" / "completion" / modelName =>
      for {
        request  <- req.as[CompletionRequest]
        response <- generateBlocking(request, ModelRef(modelName))
        ok       <- Ok(response)
      } yield {
        ok
      }
  }

  def embed(request: EmbeddingInferenceRequest, modelRef: ModelRef): IO[EmbeddingInferenceResponse] = for {
    start <- IO(System.currentTimeMillis())
    model <- IO.fromOption(models.embedding.embedders.get(modelRef))(UserError(s"model $modelRef not found"))
    docs <- IO(
      request.input.map(doc =>
        doc.`type` match {
          case Some(PromptType.Document) => model.prompt.doc + doc
          case Some(PromptType.Query)    => model.prompt.query + doc
          case Some(PromptType.Raw)      => doc.text
          case None                      => doc.text
        }
      )
    )
    results <- models.embedding.cache.getOrEmbedAndCache(modelRef, docs, model.encode)
    finish  <- IO(System.currentTimeMillis())
  } yield {
    EmbeddingInferenceResponse(results.toList.map(embed => EmbeddingOutput(embed)), took = finish - start)
  }

  def generateBlocking(request: CompletionRequest, modelRef: ModelRef): IO[CompletionResponse] = for {
    start  <- IO(System.currentTimeMillis())
    frames <- generateStreaming(request, modelRef).compile.toList
    finish <- IO(System.currentTimeMillis())
  } yield {
    CompletionResponse(frames.map(_.token).mkString(""), finish - start)
  }

  def generateStreaming(request: CompletionRequest, modelRef: ModelRef): Stream[IO, CompletionFrame] = {
    models.generative
      .generate(modelRef, request.prompt, request.max_tokens)
      .through(DurationStream.pipe(System.currentTimeMillis()))
      .map { case (token, took) =>
        CompletionFrame(token, took, false)
      }
      .through(StreamMark.pipe[CompletionFrame](tail = tok => tok.copy(last = true)))
  }
}

object InferenceRoute {
  enum PromptType {
    case Document extends PromptType
    case Query    extends PromptType
    case Raw      extends PromptType
  }

  given promptTypeEncoder: Encoder[PromptType] = Encoder.instance {
    case PromptType.Document => Json.fromString("document")
    case PromptType.Query    => Json.fromString("query")
    case PromptType.Raw      => Json.fromString("raw")
  }

  given promptTypeDecoder: Decoder[PromptType] = Decoder.decodeString.emapTry {
    case "document" => Success(Document)
    case "query"    => Success(Query)
    case "raw"      => Success(Raw)
    case other      => Failure(UserError(s"Input type '$other' not supported. Maybe try document/query/raw?"))
  }

  case class EmbeddingDocument(text: String, `type`: Option[PromptType] = None)
  given embeddingDocumentCodec: Codec[EmbeddingDocument] = deriveCodec
  case class EmbeddingInferenceRequest(input: List[EmbeddingDocument])
  given embeddingInferenceRequestCodec: Codec[EmbeddingInferenceRequest] = deriveCodec
  given requestJson: EntityDecoder[IO, EmbeddingInferenceRequest]        = jsonOf

  case class EmbeddingOutput(embedding: Array[Float])
  given embeddingOutputCodec: Codec[EmbeddingOutput] = deriveCodec

  case class EmbeddingInferenceResponse(output: List[EmbeddingOutput], took: Long)
  given embeddingInferenceResponseCodec: Codec[EmbeddingInferenceResponse] = deriveCodec
  given responseJsonEncoder: EntityEncoder[IO, EmbeddingInferenceResponse] = jsonEncoderOf
  given responseJson: EntityDecoder[IO, EmbeddingInferenceResponse]        = jsonOf

  case class CompletionRequest(prompt: String, max_tokens: Int, stream: Boolean = false)
  given completionRequestEncoder: Encoder[CompletionRequest] = deriveEncoder
  given completionRequestDecoder: Decoder[CompletionRequest] = Decoder.instance(c =>
    for {
      prompt    <- c.downField("prompt").as[String]
      maxTokens <- c.downField("max_tokens").as[Int]
      stream    <- c.downField("stream").as[Option[Boolean]].map(_.getOrElse(false))
    } yield {
      CompletionRequest(prompt, maxTokens, stream)
    }
  )
  given completionRequestJson: EntityDecoder[IO, CompletionRequest] = jsonOf

  case class CompletionResponse(output: String, took: Long)
  given completionResponseCodec: Codec[CompletionResponse]                   = deriveCodec
  given completionResponseJson: EntityDecoder[IO, CompletionResponse]        = jsonOf
  given completionResponseJsonEncoder: EntityEncoder[IO, CompletionResponse] = jsonEncoderOf

  case class CompletionFrame(token: String, took: Long, last: Boolean)
}

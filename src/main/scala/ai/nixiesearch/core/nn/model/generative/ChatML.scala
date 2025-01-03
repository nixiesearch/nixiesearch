package ai.nixiesearch.core.nn.model.generative

import cats.effect.IO
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.{EntityDecoder, EntityEncoder}
import org.http4s.circe.*

object ChatML {
  case class Message(role: String, content: String)
  case class Request(
      model: String,
      stream: Boolean,
      messages: List[Message],
      max_tokens: Option[Int] = None,
      seed: Option[Int] = None
  )

  case class Delta(content: Option[String])
  case class Choice(finish_reason: Option[String], index: Int, message: Option[Message], delta: Option[Delta])
  case class Usage(completion_tokens: Int, prompt_tokens: Int, total_tokens: Int)
  case class Timings(
      prompt_n: Int,
      prompt_ms: Float,
      prompt_per_token_ms: Float,
      prompt_per_second: Float,
      predicted_n: Int,
      predicted_ms: Float,
      predicted_per_token_ms: Float,
      predicted_per_second: Float
  )
  case class Response(
      choices: List[Choice],
      created: Long,
      model: String,
      `object`: String,
      usage: Option[Usage],
      id: String,
      system_fingerprint: String,
      timings: Option[Timings]
  )

  given deltaCodec: Codec[Delta]       = deriveCodec
  given messageCodec: Codec[Message]   = deriveCodec
  given requestCodec: Codec[Request]   = deriveCodec
  given choiceCodec: Codec[Choice]     = deriveCodec
  given usageCodec: Codec[Usage]       = deriveCodec
  given timingsCodec: Codec[Timings]   = deriveCodec
  given responseCodec: Codec[Response] = deriveCodec

  given requestEncJson: EntityEncoder[IO, Request] = jsonEncoderOf
  given requestDecJson: EntityDecoder[IO, Request] = jsonOf

  given responseEncJson: EntityEncoder[IO, Response] = jsonEncoderOf
  given responseDecJson: EntityDecoder[IO, Request]  = jsonOf
}

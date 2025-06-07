package ai.nixiesearch.core.nn.model.embedding.providers

import ai.nixiesearch.config.EmbedCacheConfig
import ai.nixiesearch.config.EmbedCacheConfig.MemoryCacheConfig
import ai.nixiesearch.config.InferenceConfig.EmbeddingInferenceModelConfig
import ai.nixiesearch.core.nn.model.embedding.EmbedModel
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.core.nn.model.embedding.providers.OpenAIEmbedModel.{
  EmbedRequest,
  OpenAIEmbeddingInferenceModelConfig,
  OpenAIResponse
}
import cats.effect.{Deferred, IO, Resource}
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.*
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.circe.*

import scala.concurrent.duration.*
import ai.nixiesearch.config.mapping.DurationJson.given
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.util.ExpBackoffRetryPolicy
import org.http4s.client.middleware.Retry
import org.http4s.headers.{Authorization, `Content-Type`}
import org.http4s.{AuthScheme, Credentials, EntityDecoder, EntityEncoder, Headers, MediaType, Method, Request, Uri}

import scala.concurrent.duration.FiniteDuration

case class OpenAIEmbedModel(client: Client[IO], endpoint: Uri, key: String, config: OpenAIEmbeddingInferenceModelConfig)
    extends EmbedModelProvider
    with Logging {
  override val model: String    = config.model
  override val provider: String = "openai"
  override val batchSize        = config.batchSize

  override def encodeBatch(task: TaskType, docs: List[String]): IO[Array[Array[Float]]] = for {
    start   <- IO(System.currentTimeMillis())
    request <- IO(
      Request[IO](
        method = Method.POST,
        uri = endpoint / "v1" / "embeddings",
        headers = Headers(
          Authorization(Credentials.Token(AuthScheme.Bearer, key)),
          `Content-Type`(MediaType.application.json)
        ),
        entity = OpenAIEmbedModel.requestJson.toEntity(
          EmbedRequest(input = docs, model = config.model, dimensions = config.dimensions)
        )
      )
    )
    response <- client.expect[OpenAIResponse](request)
    finish   <- IO(System.currentTimeMillis())
    _        <- debug(
      s"Embedded ${docs.size} docs with OpenAI ${config.model}, took ${finish - start}ms (${response.usage.total_tokens} tokens)"
    )
  } yield {
    response.data.map(_.embedding).toArray
  }

}

object OpenAIEmbedModel extends Logging {
  val SUPPORTED_MODELS = List(
    "text-embedding-3-small",
    "text-embedding-3-large",
    "text-embedding-ada-002"
  )
  val DEFAULT_ENDPOINT = "https://api.openai.com/"
  case class OpenAIEmbeddingInferenceModelConfig(
      model: String,
      timeout: FiniteDuration = 2.seconds,
      retry: Int = 1,
      endpoint: String = DEFAULT_ENDPOINT,
      dimensions: Option[Int] = None,
      batchSize: Int = 32,
      cache: EmbedCacheConfig = MemoryCacheConfig()
  ) extends EmbeddingInferenceModelConfig

  given openAIEmbeddingConfigEncoder: Encoder[OpenAIEmbeddingInferenceModelConfig] = deriveEncoder

  given openAIEmbeddingConfigDecoder: Decoder[OpenAIEmbeddingInferenceModelConfig] = Decoder.instance(c =>
    for {
      model      <- c.downField("model").as[String]
      timeout    <- c.downField("timeout").as[Option[FiniteDuration]]
      retry      <- c.downField("retry").as[Option[Int]]
      endpoint   <- c.downField("endpoint").as[Option[String]]
      dimensions <- c.downField("dimensions").as[Option[Int]]
      batchSize  <- c.downField("batch_size").as[Option[Int]]
      cache      <- c.downField("cache").as[Option[EmbedCacheConfig]]
    } yield {
      OpenAIEmbeddingInferenceModelConfig(
        model = model,
        timeout = timeout.getOrElse(2.seconds),
        retry = retry.getOrElse(1),
        endpoint = endpoint.getOrElse(DEFAULT_ENDPOINT),
        dimensions = dimensions,
        batchSize = batchSize.getOrElse(32),
        cache = cache.getOrElse(MemoryCacheConfig())
      )
    }
  )

  case class EmbedRequest(input: List[String], model: String, dimensions: Option[Int])
  case class EmbedResponse(`object`: String, embedding: Array[Float], index: Int)
  case class Usage(prompt_tokens: Int, total_tokens: Int)
  case class OpenAIResponse(`object`: String, data: List[EmbedResponse], usage: Usage)

  given requestCodec: Codec[EmbedRequest]               = deriveCodec
  given requestJson: EntityEncoder[IO, EmbedRequest]    = jsonEncoderOf
  given usageCodec: Codec[Usage]                        = deriveCodec
  given embedResponseCodec: Codec[EmbedResponse]        = deriveCodec
  given openaiResponseCodec: Codec[OpenAIResponse]      = deriveCodec
  given responseJson: EntityDecoder[IO, OpenAIResponse] = jsonOf

  def create(config: OpenAIEmbeddingInferenceModelConfig): Resource[IO, OpenAIEmbedModel] = for {
    client <- EmberClientBuilder.default[IO].withTimeout(10.seconds).build
    retryClient = Retry[IO](ExpBackoffRetryPolicy(100.millis, 2.0, 4000.millis, config.retry))(client)
    key <- Resource.eval(
      IO.fromOption(Option(System.getenv("OPENAI_KEY")))(
        Exception("OPENAI_KEY env var is missing - how should we authenticate?")
      )
    )
    _ <- Resource.eval(
      IO.whenA(!key.startsWith("sk"))(
        IO.raiseError(UserError(s"wrong format of OpenAI key: it must start with 'sk-', but got '$key'"))
      )
    )
    endpoint <- Resource.eval(IO.fromEither(Uri.fromString(config.endpoint)))
    _        <- Resource.eval(info(s"Started OpenAI embedding client, model=${config.model}"))
  } yield {
    OpenAIEmbedModel(retryClient, endpoint, key, config)
  }

}

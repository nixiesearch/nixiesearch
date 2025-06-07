package ai.nixiesearch.core.nn.model.embedding.providers

import ai.nixiesearch.config.EmbedCacheConfig
import ai.nixiesearch.config.EmbedCacheConfig.MemoryCacheConfig
import ai.nixiesearch.config.InferenceConfig.EmbeddingInferenceModelConfig
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.embedding.EmbedModel
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.core.nn.model.embedding.providers.CohereEmbedModel.{
  CohereEmbeddingInferenceModelConfig,
  EmbedRequest,
  EmbedResponse
}
import ai.nixiesearch.util.ExpBackoffRetryPolicy
import cats.effect.{IO, Resource}
import io.circe.{Codec, Decoder, Encoder}
import org.http4s.circe.*
import org.http4s.{AuthScheme, Credentials, EntityDecoder, EntityEncoder, Headers, MediaType, Method, Request, Uri}
import org.http4s.client.Client

import scala.concurrent.duration.*
import io.circe.generic.semiauto.*
import org.http4s.client.middleware.{Retry, RetryPolicy}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.{Accept, Authorization, `Content-Type`}

import scala.concurrent.duration.FiniteDuration

case class CohereEmbedModel(client: Client[IO], endpoint: Uri, key: String, config: CohereEmbeddingInferenceModelConfig)
    extends EmbedModelProvider
    with Logging {
  override val batchSize: Int   = config.batchSize
  override val model: String    = config.model
  override val provider: String = "cohere"

  override protected def encodeBatch(task: EmbedModel.TaskType, batch: List[String]): IO[Array[Array[Float]]] = for {
    start   <- IO(System.currentTimeMillis())
    request <- IO.pure(
      Request[IO](
        method = Method.POST,
        uri = endpoint / "v2" / "embed",
        headers = Headers(
          Authorization(Credentials.Token(AuthScheme.Bearer, key)),
          `Content-Type`(MediaType.application.json),
          Accept(MediaType.application.json)
        ),
        entity = CohereEmbedModel.requestJson.toEntity(
          EmbedRequest(
            model = config.model,
            texts = batch,
            input_type = task match {
              case TaskType.Document => "search_document"
              case TaskType.Query    => "search_query"
              case TaskType.Raw      => "clustering"
            },
            embedding_types = List("float")
          )
        )
      )
    )
    response <- client.expect[EmbedResponse](request)
    finish   <- IO(System.currentTimeMillis())
    _        <- debug(
      s"Embedded ${batch.size} docs with Cohere ${config.model}, took ${finish - start}ms (${response.meta.billed_units.input_tokens} tokens)"
    )

  } yield {
    response.embeddings.float
  }

}

object CohereEmbedModel extends Logging {
  import ai.nixiesearch.config.mapping.DurationJson.given

  val DEFAULT_ENDPOINT = "https://api.cohere.com/"
  val SUPPORTED_MODELS =
    List(
      "embed-english-v3.0",
      "embed-multilingual-v3.0",
      "embed-english-light-v3.0",
      "embed-multilingual-light-v3.0",
      "embed-english-v2.0",
      "embed-english-light-v2.0",
      "embed-multilingual-v2.0"
    )

  case class EmbedRequest(model: String, texts: List[String], input_type: String, embedding_types: List[String])
  given requestCodec: Codec[EmbedRequest]            = deriveCodec
  given requestJson: EntityEncoder[IO, EmbedRequest] = jsonEncoderOf

  case class Embeddings(float: Array[Array[Float]])
  case class BilledUnits(input_tokens: Int)
  case class Meta(billed_units: BilledUnits)
  case class EmbedResponse(id: String, embeddings: Embeddings, texts: List[String], meta: Meta)
  given embeddingsCodec: Codec[Embeddings]             = deriveCodec
  given billedUnitsCodec: Codec[BilledUnits]           = deriveCodec
  given metaCodec: Codec[Meta]                         = deriveCodec
  given responseCodec: Codec[EmbedResponse]            = deriveCodec
  given responseJson: EntityDecoder[IO, EmbedResponse] = jsonOf

  case class CohereEmbeddingInferenceModelConfig(
      model: String,
      timeout: FiniteDuration = 2.seconds,
      retry: Int = 1,
      endpoint: String = DEFAULT_ENDPOINT,
      batchSize: Int = 32,
      cache: EmbedCacheConfig = MemoryCacheConfig()
  ) extends EmbeddingInferenceModelConfig

  given cohereEmbeddingConfigEncoder: Encoder[CohereEmbeddingInferenceModelConfig] = deriveEncoder

  given cohereEmbeddingConfigDecoder: Decoder[CohereEmbeddingInferenceModelConfig] = Decoder.instance(c =>
    for {
      model     <- c.downField("model").as[String]
      timeout   <- c.downField("timeout").as[Option[FiniteDuration]]
      retry     <- c.downField("retry").as[Option[Int]]
      endpoint  <- c.downField("endpoint").as[Option[String]]
      batchSize <- c.downField("batch_size").as[Option[Int]]
      cache     <- c.downField("cache").as[Option[EmbedCacheConfig]]
    } yield {
      CohereEmbeddingInferenceModelConfig(
        model = model,
        timeout = timeout.getOrElse(2.seconds),
        retry = retry.getOrElse(1),
        endpoint = endpoint.getOrElse(DEFAULT_ENDPOINT),
        batchSize = batchSize.getOrElse(32),
        cache = cache.getOrElse(MemoryCacheConfig())
      )
    }
  )

  def create(config: CohereEmbeddingInferenceModelConfig): Resource[IO, CohereEmbedModel] = for {
    client <- EmberClientBuilder.default[IO].withTimeout(10.seconds).build
    retryClient = Retry[IO](ExpBackoffRetryPolicy(100.millis, 2.0, 4000.millis, config.retry))(client)
    key <- Resource.eval(
      IO.fromOption(Option(System.getenv("COHERE_KEY")))(
        Exception("COHERE_KEY env var is missing - how should we authenticate?")
      )
    )
    _        <- Resource.eval(IO.whenA(key.length < 10)(IO.raiseError(UserError(s"Cohere API key too short: '$key'"))))
    endpoint <- Resource.eval(IO.fromEither(Uri.fromString(config.endpoint)))
    _        <- Resource.eval(info(s"Started Cohere embedding client, model=${config.model}"))
  } yield {
    CohereEmbedModel(retryClient, endpoint, key, config)
  }
}

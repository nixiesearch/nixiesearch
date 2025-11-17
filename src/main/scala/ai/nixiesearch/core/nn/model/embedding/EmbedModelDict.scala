package ai.nixiesearch.core.nn.model.embedding

import ai.nixiesearch.config.EmbedCacheConfig
import ai.nixiesearch.config.EmbedCacheConfig.MemoryCacheConfig
import ai.nixiesearch.config.InferenceConfig.{EmbeddingInferenceModelConfig, PromptConfig}
import ai.nixiesearch.core.Error.{BackendError, UserError}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.metrics.Metrics
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import ai.nixiesearch.core.nn.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.nixiesearch.core.nn.huggingface.ModelFileCache
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType.Query
import ai.nixiesearch.core.nn.model.embedding.cache.{CachedEmbedModel, MemoryCachedEmbedModel}
import ai.nixiesearch.core.nn.model.embedding.providers.CohereEmbedModel.CohereEmbeddingInferenceModelConfig
import ai.nixiesearch.core.nn.model.embedding.providers.{
  CohereEmbedModel,
  EmbedModelProvider,
  OnnxEmbedModel,
  OpenAIEmbedModel
}
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel.OnnxEmbeddingInferenceModelConfig
import ai.nixiesearch.core.nn.model.embedding.providers.OpenAIEmbedModel.OpenAIEmbeddingInferenceModelConfig
import ai.nixiesearch.util.{EnvVars, Version}
import cats.effect
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fs2.io.file.Files
import fs2.io.file.Path as Fs2Path
import fs2.Stream
import io.circe.Decoder
import io.circe.parser.decode
import io.circe.generic.semiauto.*

import java.nio.file.Files as NIOFiles

case class EmbedModelDict(embedders: Map[ModelRef, EmbedModel], metrics: Metrics) extends Logging {
  def encode(name: ModelRef, task: TaskType, text: String): IO[Array[Float]] =
    encode(name, task, List(text)).flatMap {
      case head :: Nil => IO.pure(head)
      case other       => IO.raiseError(BackendError(s"expected 1 response from embed model, but got ${other.length}"))
    }
  def encode(name: ModelRef, task: TaskType, texts: List[String]): IO[List[Array[Float]]] =
    embedders.get(name) match {
      case None           => IO.raiseError(UserError(s"cannot get embedding model $name"))
      case Some(embedder) =>
        for {
          _      <- IO(metrics.inference.embedTotal.labelValues(name.name).inc())
          _      <- IO(metrics.inference.embedDocTotal.labelValues(name.name).inc(texts.size))
          start  <- IO(System.currentTimeMillis())
          result <- embedder.encode(task, texts).compile.toList
          finish <- IO(System.currentTimeMillis())
          _      <- IO(metrics.inference.embedTimeSeconds.labelValues(name.name).inc((finish - start) / 1000.0))
        } yield result
    }

}

object EmbedModelDict extends Logging {

  case class TransformersConfig(
      hidden_size: Int,
      model_type: Option[String] = None,
      num_hidden_layers: Option[Int] = None,
      num_attention_heads: Option[Int] = None,
      num_key_value_heads: Option[Int] = None
  )
  given transformersConfigDecoder: Decoder[TransformersConfig] = deriveDecoder

  def create(
      models: Map[ModelRef, EmbeddingInferenceModelConfig],
      localFileCache: ModelFileCache,
      metrics: Metrics,
      env: EnvVars
  ): Resource[IO, EmbedModelDict] =
    for {
      encoders <- models.toList.map {
        case (name: ModelRef, conf: OnnxEmbeddingInferenceModelConfig) if Version.isGraalVMNativeImage =>
          Resource.raiseError[IO, (ModelRef, EmbedModel), Throwable](
            UserError(
              s"model ${name.name} is an ONNX model, which is not supported on native images. Either switch to JDK, or use API-based embedding providers"
            )
          )
        case (name: ModelRef, conf: OnnxEmbeddingInferenceModelConfig) =>
          OnnxEmbedModel
            .create(conf.model, conf, localFileCache)
            .flatMap(maybeCache(_, conf.cache).map(emb => name -> emb))
        case (name: ModelRef, conf: OpenAIEmbeddingInferenceModelConfig) =>
          OpenAIEmbedModel
            .create(conf, env)
            .flatMap(maybeCache(_, conf.cache).map(emb => name -> emb))
        case (name: ModelRef, conf: CohereEmbeddingInferenceModelConfig) =>
          CohereEmbedModel
            .create(conf, env)
            .flatMap(maybeCache(_, conf.cache).map(emb => name -> emb))
        case (name: ModelRef, other) =>
          Resource.raiseError[IO, (ModelRef, EmbedModel), Throwable](
            BackendError(s"Don't know how to init, $other embed model, this is a bug!")
          )
      }.sequence
    } yield {
      EmbedModelDict(encoders.toMap, metrics)
    }

  private def maybeCache(model: EmbedModel, cache: EmbedCacheConfig): Resource[IO, EmbedModel] = cache match {
    case EmbedCacheConfig.NoCache => Resource.pure(model)
    case mem: MemoryCacheConfig   => MemoryCachedEmbedModel.create(model, mem)
  }
}

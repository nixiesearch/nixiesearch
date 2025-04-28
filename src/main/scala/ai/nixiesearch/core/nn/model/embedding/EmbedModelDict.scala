package ai.nixiesearch.core.nn.model.embedding

import ai.nixiesearch.config.EmbedCacheConfig
import ai.nixiesearch.config.EmbedCacheConfig.MemoryCacheConfig
import ai.nixiesearch.config.InferenceConfig.{EmbeddingInferenceModelConfig, PromptConfig}
import ai.nixiesearch.core.Error.{BackendError, UserError}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import ai.nixiesearch.core.nn.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType.Query
import ai.nixiesearch.core.nn.model.{HuggingFaceClient, ModelFileCache}
import ai.nixiesearch.core.nn.model.embedding.cache.{CachedEmbedModel, MemoryCachedEmbedModel}
import ai.nixiesearch.core.nn.model.embedding.providers.CohereEmbedModel.CohereEmbeddingInferenceModelConfig
import ai.nixiesearch.core.nn.model.embedding.providers.{CohereEmbedModel, OnnxEmbedModel, OpenAIEmbedModel}
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel.OnnxEmbeddingInferenceModelConfig
import ai.nixiesearch.core.nn.model.embedding.providers.OpenAIEmbedModel.OpenAIEmbeddingInferenceModelConfig
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

case class EmbedModelDict(embedders: Map[ModelRef, EmbedModel]) extends Logging {
  def encode(name: ModelRef, task: TaskType, text: String): IO[Array[Float]] =
    encode(name, task, List(text)).flatMap {
      case head :: Nil => IO.pure(head)
      case other       => IO.raiseError(BackendError(s"expected 1 response from embed model, but got ${other.length}"))
    }
  def encode(name: ModelRef, task: TaskType, texts: List[String]): IO[List[Array[Float]]] =
    embedders.get(name) match {
      case None           => IO.raiseError(UserError(s"cannot get embedding model $name"))
      case Some(embedder) => embedder.encode(task, texts).compile.toList
    }
  
}

object EmbedModelDict extends Logging {
  val CONFIG_FILE = "config.json"

  case class TransformersConfig(hidden_size: Int, model_type: Option[String])
  given transformersConfigDecoder: Decoder[TransformersConfig] = deriveDecoder

  def create(
      models: Map[ModelRef, EmbeddingInferenceModelConfig],
      localFileCache: ModelFileCache
  ): Resource[IO, EmbedModelDict] =
    for {
      encoders <- models.toList.map {
        case (name: ModelRef, conf: OnnxEmbeddingInferenceModelConfig) =>
          conf.model match {
            case handle: HuggingFaceHandle =>
              OnnxEmbedModel
                .createHuggingface(handle, conf, localFileCache)
                .flatMap(maybeCache(_, conf.cache).map(emb => name -> emb))
            case handle: LocalModelHandle =>
              OnnxEmbedModel
                .createLocal(handle, conf)
                .flatMap(maybeCache(_, conf.cache).map(emb => name -> emb))
          }
        case (name: ModelRef, conf: OpenAIEmbeddingInferenceModelConfig) =>
          OpenAIEmbedModel
            .create(conf)
            .flatMap(maybeCache(_, conf.cache).map(emb => name -> emb))
        case (name: ModelRef, conf: CohereEmbeddingInferenceModelConfig) =>
          CohereEmbedModel
            .create(conf)
            .flatMap(maybeCache(_, conf.cache).map(emb => name -> emb))
      }.sequence
    } yield {
      EmbedModelDict(encoders.toMap)
    }

  private def maybeCache(model: EmbedModel, cache: EmbedCacheConfig): Resource[IO, EmbedModel] = cache match {
    case EmbedCacheConfig.NoCache => Resource.pure(model)
    case mem: MemoryCacheConfig   => MemoryCachedEmbedModel.create(model, mem)
  }
}

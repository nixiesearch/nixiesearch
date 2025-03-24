package ai.nixiesearch.core.nn.model.embedding

import ai.nixiesearch.config.IndexCacheConfig.EmbeddingCacheConfig
import ai.nixiesearch.config.InferenceConfig.{EmbeddingInferenceModelConfig, PromptConfig}
import ai.nixiesearch.core.Error.{BackendError, UserError}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import ai.nixiesearch.core.nn.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.core.nn.model.{HuggingFaceClient, ModelFileCache}
import ai.nixiesearch.core.nn.model.embedding.cache.{EmbeddingCache, HeapEmbeddingCache}
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel.OnnxEmbeddingInferenceModelConfig
import ai.nixiesearch.core.nn.model.embedding.providers.OpenAIEmbedModel
import ai.nixiesearch.core.nn.model.embedding.providers.OpenAIEmbedModel.OpenAIEmbeddingInferenceModelConfig
import cats.effect
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits.*
import fs2.io.file.Files
import fs2.io.file.Path as Fs2Path
import io.circe.Decoder
import io.circe.parser.decode
import io.circe.generic.semiauto.*

import java.nio.file.Files as NIOFiles

case class EmbedModelDict(embedders: Map[ModelRef, EmbedModel], cache: EmbeddingCache) extends Logging {
  def encodeQuery(handle: ModelRef, query: String): IO[Array[Float]] = IO(embedders.get(handle)).flatMap {
    case None => IO.raiseError(new Exception(s"cannot get embedding model $handle"))
    case Some(embedder) =>
      cache.getOrEmbedAndCache(handle, TaskType.Query, List(query), embedder.encode).flatMap {
        case x if x.length == 1 => IO.pure(x(0))
        case other => IO.raiseError(BackendError(s"embedder expected to return 1 result, but got ${other.length}"))
      }
  }
  def encodeDocuments(handle: ModelRef, docs: List[String]): IO[Array[Array[Float]]] =
    IO(embedders.get(handle)).flatMap {
      case None =>
        IO.raiseError(
          new Exception(
            s"Embedding model '${handle.name}' is referenced in the index mapping, but not defined in the inference config."
          )
        )
      case Some(embedder) =>
        cache.getOrEmbedAndCache(handle, TaskType.Document, docs, embedder.encode)
    }

}

object EmbedModelDict extends Logging {
  val CONFIG_FILE = "config.json"

  case class TransformersConfig(hidden_size: Int, model_type: Option[String])
  given transformersConfigDecoder: Decoder[TransformersConfig] = deriveDecoder

  def create(
      models: Map[ModelRef, EmbeddingInferenceModelConfig],
      cache: ModelFileCache
  ): Resource[IO, EmbedModelDict] =
    for {
      encoders <- models.toList.map {
        case (name: ModelRef, conf @ OnnxEmbeddingInferenceModelConfig(handle: HuggingFaceHandle, _, _, _, _, _, _)) =>
          OnnxEmbedModel.createHuggingface(handle, conf, cache).map(embedder => name -> embedder)
        case (name: ModelRef, conf @ OnnxEmbeddingInferenceModelConfig(handle: LocalModelHandle, _, _, _, _, _, _)) =>
          OnnxEmbedModel.createLocal(handle, conf).map(embedder => name -> embedder)
        case (name: ModelRef, conf: OpenAIEmbeddingInferenceModelConfig) =>
          OpenAIEmbedModel.create(conf).map(embedder => name -> embedder)
      }.sequence
      cache <- HeapEmbeddingCache.create(EmbeddingCacheConfig())
    } yield {
      EmbedModelDict(encoders.toMap, cache)
    }

}

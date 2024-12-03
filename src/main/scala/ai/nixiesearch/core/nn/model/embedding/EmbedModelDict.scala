package ai.nixiesearch.core.nn.model.embedding

import ai.nixiesearch.config.IndexCacheConfig.EmbeddingCacheConfig
import ai.nixiesearch.config.InferenceConfig.EmbeddingInferenceModelConfig
import ai.nixiesearch.config.InferenceConfig.EmbeddingInferenceModelConfig.{
  OnnxEmbeddingInferenceModelConfig,
  OpenAIEmbeddingInferenceModelConfig
}
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import ai.nixiesearch.core.nn.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.OnnxEmbedModel
import ai.nixiesearch.core.nn.model.{HuggingFaceClient, ModelFileCache}
import ai.nixiesearch.core.nn.model.embedding.cache.{EmbeddingCache, HeapEmbeddingCache}
import ai.nixiesearch.core.nn.model.embedding.cache.EmbeddingCache.CacheKey
import cats.effect
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits.*
import fs2.io.file.Files
import fs2.io.file.Path as Fs2Path
import io.circe.Decoder
import io.circe.parser.decode
import io.circe.generic.semiauto.*

import java.io.FileInputStream
import java.nio.file.Files as NIOFiles

case class EmbedModelDict(embedders: Map[ModelRef, EmbedModel], cache: EmbeddingCache) extends Logging {
  def encodeQuery(handle: ModelRef, query: String): IO[Array[Float]] = IO(embedders.get(handle)).flatMap {
    case None => IO.raiseError(new Exception(s"cannot get embedding model $handle"))
    case Some(embedder) =>
      val formattedQuery = embedder.prompt.query + query
      cache.getOrEmbedAndCache(handle, List(formattedQuery), embedder.encode).flatMap {
        case x if x.length == 1 => IO.pure(x(0))
        case other => IO.raiseError(BackendError(s"embedder expected to return 1 result, but got ${other.length}"))
      }
  }
  def encodeDocuments(handle: ModelRef, docs: List[String]): IO[Array[Array[Float]]] =
    IO(embedders.get(handle)).flatMap {
      case None => IO.raiseError(new Exception(s"cannot get embedding model $handle"))
      case Some(embedder) =>
        val formattedDocs = docs.map(doc => embedder.prompt.doc + doc)
        cache.getOrEmbedAndCache(handle, formattedDocs, embedder.encode)
    }

}

object EmbedModelDict extends Logging {
  val CONFIG_FILE = "config.json"

  case class TransformersConfig(hidden_size: Int, model_type: String)
  given transformersConfigDecoder: Decoder[TransformersConfig] = deriveDecoder

  def create(
      models: Map[ModelRef, EmbeddingInferenceModelConfig],
      cache: ModelFileCache
  ): Resource[IO, EmbedModelDict] =
    for {
      encoders <- models.toList.map {
        case (name: ModelRef, conf @ OnnxEmbeddingInferenceModelConfig(handle: HuggingFaceHandle, _, _, _, _)) =>
          createHuggingface(handle, conf, cache).map(embedder => name -> embedder)
        case (name: ModelRef, conf @ OnnxEmbeddingInferenceModelConfig(handle: LocalModelHandle, _, _, _, _)) =>
          createLocal(handle, conf).map(embedder => name -> embedder)
        case (name: ModelRef, conf @ OpenAIEmbeddingInferenceModelConfig(model)) =>
          Resource.raiseError[IO, (ModelRef, EmbedModel), Throwable](BackendError("not yet implemented"))
      }.sequence
      cache <- HeapEmbeddingCache.create(EmbeddingCacheConfig())
    } yield {
      EmbedModelDict(encoders.toMap, cache)
    }

  def createHuggingface(
      handle: HuggingFaceHandle,
      conf: OnnxEmbeddingInferenceModelConfig,
      cache: ModelFileCache
  ): Resource[IO, EmbedModel] = for {
    hf <- HuggingFaceClient.create(cache)
    (model, vocab, config) <- Resource.eval(for {
      card      <- hf.model(handle)
      modelFile <- chooseModelFile(card.siblings.map(_.rfilename), conf.file)
      tokenizerFile <- IO.fromOption(card.siblings.map(_.rfilename).find(_ == "tokenizer.json"))(
        BackendError("Cannot find tokenizer.json in repo")
      )
      _         <- info(s"Fetching $handle from HF: model=$modelFile tokenizer=$tokenizerFile")
      modelPath <- hf.getCached(handle, modelFile)
      vocabPath <- hf.getCached(handle, tokenizerFile)
      config <- hf
        .getCached(handle, CONFIG_FILE)
        .flatMap(path => IO.fromEither(decode[TransformersConfig](NIOFiles.readString(path))))
    } yield {
      (modelPath, vocabPath, config)
    })
    onnxEmbedder <- OnnxEmbedModel.create(
      model = model,
      dic = vocab,
      dim = config.hidden_size,
      prompt = conf.prompt,
      seqlen = conf.maxTokens
    )
  } yield {
    onnxEmbedder
  }
  def createLocal(handle: LocalModelHandle, conf: OnnxEmbeddingInferenceModelConfig): Resource[IO, EmbedModel] = {
    for {
      (model, vocab, config) <- Resource.eval(for {
        path      <- IO(Fs2Path(handle.dir))
        files     <- fs2.io.file.Files[IO].list(path).map(_.fileName.toString).compile.toList
        modelFile <- chooseModelFile(files, conf.file)
        tokenizerFile <- IO.fromOption(files.find(_ == "tokenizer.json"))(
          BackendError("cannot find tokenizer.json file in dir")
        )
        _      <- info(s"loading $modelFile from $handle")
        config <- IO.fromEither(decode[TransformersConfig](NIOFiles.readString(path.toNioPath.resolve(CONFIG_FILE))))
      } yield {
        (path.toNioPath.resolve(modelFile), path.toNioPath.resolve(tokenizerFile), config)
      })
      onnxEmbedder <- OnnxEmbedModel.create(
        model = model,
        dic = vocab,
        dim = config.hidden_size,
        prompt = conf.prompt,
        seqlen = conf.maxTokens
      )
    } yield {
      onnxEmbedder
    }
  }

  val DEFAULT_MODEL_FILES = Set(
    "model_quantized.onnx",
    "model_opt0_QInt8.onnx",
    "model_opt2_QInt8.onnx",
    "model.onnx"
  )
  def chooseModelFile(files: List[String], forced: Option[String]): IO[String] = {
    forced match {
      case Some(f) => IO.pure(f)
      case None =>
        files.find(x => DEFAULT_MODEL_FILES.contains(x)) match {
          case Some(value) => info(s"loading $value") *> IO.pure(value)
          case None =>
            files.find(_ == "model.onnx") match {
              case Some(value) => info(s"loading regular FP32 $value") *> IO.pure(value)
              case None =>
                files.find(_.endsWith("onnx")) match {
                  case Some(value) => IO.pure(value)
                  case None        => IO.raiseError(BackendError(s"cannot find onnx model: files=$files"))
                }
            }
        }
    }

  }
}

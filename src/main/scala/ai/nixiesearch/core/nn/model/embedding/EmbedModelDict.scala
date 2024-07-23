package ai.nixiesearch.core.nn.model.embedding

import ai.nixiesearch.config.IndexCacheConfig.EmbeddingCacheConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.SemanticSearch
import ai.nixiesearch.config.{CacheConfig, Config, FieldSchema}
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle
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
import org.apache.commons.io.IOUtils
import io.circe.generic.semiauto.*
import java.io.{ByteArrayInputStream, File, FileInputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Paths, Files as NIOFiles}
import scala.collection.mutable.ArrayBuffer

case class EmbedModelDict(embedders: Map[ModelHandle, EmbedModel], cache: EmbeddingCache) extends Logging {
  def encode(handle: ModelHandle, doc: String): IO[Array[Float]] = IO(embedders.get(handle)).flatMap {
    case None => IO.raiseError(new Exception(s"cannot get embedding model $handle"))
    case Some(embedder) =>
      cache.getOrEmbedAndCache(List(CacheKey(handle, doc)), embedder).flatMap {
        case x if x.length == 1 => IO.pure(x(0))
        case other              => IO.raiseError(BackendError(s"embedder expected to return 1 result, but got $other"))
      }
  }
  def encode(handle: ModelHandle, docs: List[String]): IO[Array[Array[Float]]] = IO(embedders.get(handle)).flatMap {
    case None => IO.raiseError(new Exception(s"cannot get embedding model $handle"))
    case Some(embedder) =>
      cache.getOrEmbedAndCache(docs.map(doc => CacheKey(handle, doc)), embedder)
  }

}

object EmbedModelDict extends Logging {
  val CONFIG_FILE = "config.json"

  case class TransformersConfig(hidden_size: Int, model_type: String)
  given transformersConfigDecoder: Decoder[TransformersConfig] = deriveDecoder

  def create(handles: List[ModelHandle], cacheConfig: CacheConfig): Resource[IO, EmbedModelDict] = for {
    cache <- Resource.eval(ModelFileCache.create(cacheConfig))
    encoders <- handles.map {
      case handle: HuggingFaceHandle => createHuggingface(handle, cache).map(embedder => handle -> embedder)
      case handle: LocalModelHandle  => createLocal(handle).map(embedder => handle -> embedder)
    }.sequence
    cache <- HeapEmbeddingCache.create(EmbeddingCacheConfig())
  } yield {
    EmbedModelDict(encoders.toMap, cache)
  }

  def createHuggingface(handle: HuggingFaceHandle, cache: ModelFileCache): Resource[IO, EmbedModel] = for {
    hf <- HuggingFaceClient.create(cache)
    (model, vocab, config) <- Resource.eval(for {
      card      <- hf.model(handle)
      modelFile <- chooseModelFile(card.siblings.map(_.rfilename))
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
      model = new FileInputStream(model.toFile),
      dic = new FileInputStream(vocab.toFile),
      dim = config.hidden_size
    )
  } yield {
    onnxEmbedder
  }
  def createLocal(handle: LocalModelHandle): Resource[IO, EmbedModel] = {
    for {
      (model, vocab, config) <- Resource.eval(for {
        path      <- IO(Fs2Path(handle.dir))
        files     <- fs2.io.file.Files[IO].list(path).map(_.fileName.toString).compile.toList
        modelFile <- chooseModelFile(files)
        tokenizerFile <- IO.fromOption(files.find(_ == "tokenizer.json"))(
          BackendError("cannot find tokenizer.json file in dir")
        )
        _      <- info(s"loading $modelFile from $handle")
        config <- IO.fromEither(decode[TransformersConfig](NIOFiles.readString(path.toNioPath.resolve(CONFIG_FILE))))
      } yield {
        (path.toNioPath.resolve(modelFile), path.toNioPath.resolve(tokenizerFile), config)
      })
      onnxEmbedder <- OnnxEmbedModel.create(
        model = new FileInputStream(model.toFile),
        dic = new FileInputStream(vocab.toFile),
        dim = config.hidden_size
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
  def chooseModelFile(files: List[String]): IO[String] = files.find(x => DEFAULT_MODEL_FILES.contains(x)) match {
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

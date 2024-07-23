package ai.nixiesearch.core.nn.model.embedding

import ai.nixiesearch.config.IndexCacheConfig.EmbeddingCacheConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.SemanticSearch
import ai.nixiesearch.config.{CacheConfig, Config, FieldSchema}
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.nixiesearch.core.nn.model.embedding.Embedder.OnnxEmbedder
import ai.nixiesearch.core.nn.model.{HuggingFaceClient, ModelCache}
import ai.nixiesearch.core.nn.model.embedding.cache.{EmbedderCache, HeapEmbedderCache}
import ai.nixiesearch.core.nn.model.embedding.cache.EmbedderCache.CacheKey
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

case class EmbedderDict(embedders: Map[ModelHandle, Embedder], cache: EmbedderCache) extends Logging {
  def encode(handle: ModelHandle, doc: String): IO[Array[Float]] = IO(embedders.get(handle)).flatMap {
    case None => IO.raiseError(new Exception(s"cannot get embedding model $handle"))
    case Some(embedder) =>
      cache.get(CacheKey(handle, doc)).flatMap {
        case Some(found) => IO.pure(found)
        case None        => embedder.encode(doc)
      }
  }
  def encode(handle: ModelHandle, docs: List[String]): IO[Array[Array[Float]]] = IO(embedders.get(handle)).flatMap {
    case None => IO.raiseError(new Exception(s"cannot get embedding model $handle"))
    case Some(embedder) =>
      for {
        cached                            <- cache.get(docs.map(doc => CacheKey(handle, doc)))
        (nonCachedIndices, nonCachedDocs) <- selectUncached(cached, docs.toArray)
        nonCachedEmbeddings               <- embedder.encode(nonCachedDocs.toList)
        _      <- cache.put(nonCachedDocs.map(doc => CacheKey(handle, doc)).toList, nonCachedEmbeddings)
        merged <- mergeCachedUncached(cached, nonCachedIndices, nonCachedEmbeddings)
      } yield {
        merged
      }
  }

  private def selectUncached(
      cached: Array[Option[Array[Float]]],
      docs: Array[String]
  ): IO[(Array[Int], Array[String])] = IO {
    val indices = ArrayBuffer[Int]()
    val ds      = ArrayBuffer[String]()
    var i       = 0
    while (i < cached.length) {
      if (cached(i).isEmpty) {
        ds.append(docs(i))
        indices.append(i)
      }
      i += 1
    }
    (indices.toArray, ds.toArray)
  }

  private def mergeCachedUncached(
      cached: Array[Option[Array[Float]]],
      uncachedIndices: Array[Int],
      uncachedEmbeds: Array[Array[Float]]
  ): IO[Array[Array[Float]]] = IO {
    val result = new Array[Array[Float]](cached.length)
    var i      = 0
    while (i < cached.length) {
      cached(i).foreach(emb => result(i) = emb)
      i += 1
    }
    var j = 0
    while (j < uncachedIndices.length) {
      val index = uncachedIndices(j)
      result(index) = uncachedEmbeds(j)
      j += 1
    }
    result
  }

}

object EmbedderDict extends Logging {
  val CONFIG_FILE = "config.json"

  case class TransformersConfig(hidden_size: Int, model_type: String)
  given transformersConfigDecoder: Decoder[TransformersConfig] = deriveDecoder

  def create(handles: List[ModelHandle], cacheConfig: CacheConfig): Resource[IO, EmbedderDict] = for {
    cache <- Resource.eval(ModelCache.create(cacheConfig))
    encoders <- handles.map {
      case handle: HuggingFaceHandle => createHuggingface(handle, cache).map(embedder => handle -> embedder)
      case handle: LocalModelHandle  => createLocal(handle).map(embedder => handle -> embedder)
    }.sequence
    cache <- HeapEmbedderCache.create(EmbeddingCacheConfig())
  } yield {
    EmbedderDict(encoders.toMap, cache)
  }

  def createHuggingface(handle: HuggingFaceHandle, cache: ModelCache): Resource[IO, Embedder] = for {
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
    onnxEmbedder <- OnnxEmbedder.create(
      model = new FileInputStream(model.toFile),
      dic = new FileInputStream(vocab.toFile),
      dim = config.hidden_size
    )
  } yield {
    onnxEmbedder
  }
  def createLocal(handle: LocalModelHandle): Resource[IO, Embedder] = {
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
      onnxEmbedder <- OnnxEmbedder.create(
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

package ai.nixiesearch.core.nn.model.embedding

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.nixiesearch.config.InferenceConfig.PromptConfig
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.util.GPUUtils
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel
import ai.onnxruntime.providers.OrtCUDAProviderOptions
import ai.onnxruntime.{OnnxTensor, OrtEnvironment, OrtLoggingLevel, OrtSession}
import cats.effect
import cats.effect.IO
import cats.effect.kernel.Resource
import org.apache.commons.io.{FileUtils, IOUtils}

import scala.jdk.CollectionConverters.*
import java.io.{InputStream, RandomAccessFile}
import java.nio.LongBuffer
import fs2.{Chunk, Stream}

import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.nio.file.Path

trait EmbedModel extends Logging {
  def batchSize: Int

  def encode(task: TaskType, doc: String): IO[Array[Float]] = encodeBatch(task, List(doc)).flatMap {
    case x if x.length == 1 => IO(x(0))
    case _                  => IO.raiseError(BackendError(s"got empty embedding for doc '$doc'"))
  }
  def encode(task: TaskType, docs: List[String]): IO[Array[Array[Float]]] = Stream
    .emits(docs)
    .chunkN(batchSize)
    .evalMap(batch => encodeBatch(task, batch.toList).map(batch => Chunk.array(batch)))
    .unchunks
    .compile
    .toList
    .map(_.toArray)

  protected def encodeBatch(task: TaskType, batch: List[String]): IO[Array[Array[Float]]]
  def close(): IO[Unit]
}

object EmbedModel {
  enum TaskType {
    case Document extends TaskType
    case Query    extends TaskType
    case Raw      extends TaskType
  }
}

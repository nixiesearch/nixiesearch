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
  def model: String
  def provider: String
  def batchSize: Int

  def encode(task: TaskType, docs: List[String]): Stream[IO, Array[Float]]
}

object EmbedModel {
  enum TaskType(val name: String) {
    case Document extends TaskType("doc")
    case Query    extends TaskType("query")
    case Raw      extends TaskType("raw")
  }
}

package ai.nixiesearch.core.nn.model

import ai.djl.modality.nlp.DefaultVocabulary
import ai.djl.modality.nlp.bert.BertFullTokenizer
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.model.loader.ModelLoader
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel
import ai.onnxruntime.{OrtEnvironment, OrtSession, TensorInfo}
import cats.effect.IO
import org.apache.commons.io.{FileUtils, IOUtils}

import java.io.InputStream
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

case class OnnxSession(env: OrtEnvironment, session: OrtSession, tokenizer: BertFullTokenizer, dim: Int) {
  def close(): Unit = {
    session.close()
    env.close()
  }
}

object OnnxSession extends Logging {
  import ai.nixiesearch.core.nn.model.loader.HuggingFaceModelLoader.*
  import ai.nixiesearch.core.nn.model.loader.LocalModelLoader.*

  def load(
      handle: ModelHandle,
      dim: Int,
      modelFile: String = "pytorch_model.onnx",
      vocabFile: String = "vocab.txt"
  ): IO[OnnxSession] =
    handle match {
      case hh: HuggingFaceHandle => load(hh, dim, modelFile, vocabFile)
      case lh: LocalModelHandle  => load(lh, dim, modelFile, vocabFile)
    }

  def load[T <: ModelHandle](handle: T, dim: Int, modelFile: String, vocabFile: String)(implicit
      loader: ModelLoader[T]
  ): IO[OnnxSession] = loader.load(handle, dim, modelFile, vocabFile)

  def load(model: InputStream, dic: InputStream, dim: Int): IO[OnnxSession] = IO {
    val tokens    = IOUtils.toString(dic, StandardCharsets.UTF_8).split('\n')
    val vocab     = DefaultVocabulary.builder().add(tokens.toList.asJava).build()
    val tokenizer = new BertFullTokenizer(vocab, true)
    val env       = OrtEnvironment.getEnvironment("sbert")
    val opts      = new SessionOptions()
    opts.setIntraOpNumThreads(Runtime.getRuntime.availableProcessors())
    opts.setOptimizationLevel(OptLevel.ALL_OPT)
    val modelBytes = IOUtils.toByteArray(model)
    val session    = env.createSession(modelBytes)
    val size       = FileUtils.byteCountToDisplaySize(modelBytes.length)
    val inputs     = session.getInputNames.asScala.toList
    val outputs    = session.getOutputNames.asScala.toList
    logger.info(s"Loaded ONNX model (size=$size inputs=$inputs outputs=$outputs dim=$dim)")
    OnnxSession(env, session, tokenizer, dim)
  }

}

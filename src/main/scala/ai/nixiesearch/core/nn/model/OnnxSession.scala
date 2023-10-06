package ai.nixiesearch.core.nn.model

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.djl.modality.nlp.DefaultVocabulary
import ai.djl.modality.nlp.bert.BertFullTokenizer
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.model.loader.{HuggingFaceModelLoader, LocalModelLoader, ModelLoader}
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.OrtSession.SessionOptions.{ExecutionMode, OptLevel}
import ai.onnxruntime.{OrtEnvironment, OrtLoggingLevel, OrtSession, TensorInfo}
import cats.effect.IO
import org.apache.commons.io.{FileUtils, IOUtils}

import java.io.InputStream
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

case class OnnxSession(env: OrtEnvironment, session: OrtSession, tokenizer: HuggingFaceTokenizer, dim: Int) {
  def close(): Unit = {
    session.close()
    env.close()
  }
}

object OnnxSession extends Logging {
  import ai.nixiesearch.core.nn.model.loader.HuggingFaceModelLoader.*
  import ai.nixiesearch.core.nn.model.loader.LocalModelLoader.*

  def load(handle: ModelHandle): IO[OnnxSession] =
    handle match {
      case hh: HuggingFaceHandle => HuggingFaceModelLoader.load(hh)
      case lh: LocalModelHandle  => LocalModelLoader.load(lh)
    }

  def load(model: InputStream, dic: InputStream, dim: Int): IO[OnnxSession] = IO {
    val tokenizer = HuggingFaceTokenizer.newInstance(dic, Map("padding" -> "true", "truncation" -> "true").asJava)
    val env       = OrtEnvironment.getEnvironment("sbert")
    val opts      = new SessionOptions()
    // opts.setIntraOpNumThreads(Runtime.getRuntime.availableProcessors())
    opts.setOptimizationLevel(OptLevel.ALL_OPT)
    // opts.addCUDA()
    val modelBytes = IOUtils.toByteArray(model)
    val session    = env.createSession(modelBytes, opts)
    val size       = FileUtils.byteCountToDisplaySize(modelBytes.length)
    val inputs     = session.getInputNames.asScala.toList
    val outputs    = session.getOutputNames.asScala.toList
    logger.info(s"Loaded ONNX model (size=$size inputs=$inputs outputs=$outputs dim=$dim)")
    OnnxSession(env, session, tokenizer, dim)
  }

}

package ai.nixiesearch.core.nn.model.loader

import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.model.OnnxSession
import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.*

trait ModelLoader[T <: ModelHandle] {
  val VOCAB_FILE  = "vocab.txt"
  val CONFIG_FILE = "config.json"
  val MODEL_FILE  = "pytorch_model.onnx"
  def load(handle: T, modelFile: String): IO[OnnxSession]
}

object ModelLoader {
  case class TransformersConfig(hidden_size: Int, model_type: String)
  given transformersConfigDecoder: Decoder[TransformersConfig] = deriveDecoder
}

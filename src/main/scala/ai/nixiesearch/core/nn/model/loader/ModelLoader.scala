package ai.nixiesearch.core.nn.model.loader

import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.model.OnnxSession
import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.*

trait ModelLoader[T <: ModelHandle] extends Logging {
  val CONFIG_FILE = "config.json"
  def load(handle: T): IO[OnnxSession]

  def chooseModelFile(files: List[String]): IO[String] = files.find(_ == "model_quantized.onnx") match {
    case Some(value) => info("loading quantized model_quantized.onnx") *> IO.pure(value)
    case None =>
      files.find(_ == "model.onnx") match {
        case Some(value) => info("loading regular FP32 model.onnx") *> IO.pure(value)
        case None =>
          files.find(_.endsWith("onnx")) match {
            case Some(value) => IO.pure(value)
            case None => IO.raiseError(BackendError(s"cannot find onnx model: files=$files"))
          }
      }
  }

}

object ModelLoader {
  case class TransformersConfig(hidden_size: Int, model_type: String)
  given transformersConfigDecoder: Decoder[TransformersConfig] = deriveDecoder
}

package ai.nixiesearch.core.nn.onnx

import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.nn.onnx.OnnxConfig.Device
import io.circe.{Decoder, Encoder, Json}

import scala.util.{Failure, Success}

trait OnnxConfig {
  def file: Option[OnnxModelFile]
  def maxTokens: Int
  def device: Device
  def paddingSide: Option[OnnxConfig.PaddingSide] = None
}

object OnnxConfig {
  enum PaddingSide(val value: String) {
    case Left extends PaddingSide("left")
    case Right extends PaddingSide("right")
  }

  given paddingSideEncoder: Encoder[PaddingSide] = Encoder.encodeString.contramap(_.value)

  given paddingSideDecoder: Decoder[PaddingSide] = Decoder.decodeString.emapTry {
    case "left"  => Success(PaddingSide.Left)
    case "right" => Success(PaddingSide.Right)
    case other   => Failure(UserError(s"padding_side must be 'left' or 'right', got '$other'"))
  }

  enum Device {
    case CPU(threads: Int = OnnxSession.ONNX_THREADS_DEFAULT) extends Device
    case CUDA(id: Int)                                        extends Device

  }

  given deviceEncoder: Encoder[Device] = Encoder.encodeString.contramap {
    case Device.CUDA(id)     => s"cuda:$id"
    case Device.CPU(threads) => s"cpu:$threads"
  }

  val cudaPattern                      = "cuda:([0-9]+)".r
  val cpuPattern                       = "cpu:([0-9]+)".r
  given deviceDecoder: Decoder[Device] = Decoder.decodeString.emapTry {
    case "cpu"                         => Success(Device.CPU())
    case "cuda"                        => Success(Device.CUDA(0))
    case value @ cudaPattern(idString) =>
      idString.toIntOption match {
        case Some(id) if id >= 0 => Success(Device.CUDA(id))
        case _                   => Failure(UserError(s"device id $idString should be a number"))
      }
    case value @ cpuPattern(threadsString) =>
      threadsString.toIntOption match {
        case Some(threads) if threads > 0 => Success(Device.CPU(threads))
        case _                            => Failure(UserError(s"threadpool size $threadsString should be a number"))
      }
    case other => Failure(UserError(s"device type $other is not supported"))
  }
}

package ai.nixiesearch.core.nn.model.ranking

import ai.nixiesearch.core.Error.UserError
import io.circe.{Decoder, Encoder}

import scala.util.{Failure, Success}

sealed trait LogitsProcessor {
  def process(logits: Array[Float]): Array[Float]
}

object LogitsProcessor {
  case object Sigmoid extends LogitsProcessor {
    override def process(logits: Array[Float]): Array[Float] = {
      val result = new Array[Float](logits.length)
      var i      = 0
      while (i < logits.length) {
        result(i) = (1.0f / (1.0f + math.exp(-logits(i)).toFloat))
        i += 1
      }
      result
    }
  }

  case object Noop extends LogitsProcessor {
    override def process(logits: Array[Float]): Array[Float] = logits
  }

  given logitsProcessorEncoder: Encoder[LogitsProcessor] = Encoder.encodeString.contramap {
    case Sigmoid => "sigmoid"
    case Noop    => "noop"
  }

  given logitsProcessorDecoder: Decoder[LogitsProcessor] = Decoder.decodeString.emapTry {
    case "sigmoid" => Success(Sigmoid)
    case "noop"    => Success(Noop)
    case other     => Failure(UserError(s"logits_processor must be 'sigmoid' or 'noop', got '$other'"))
  }
}

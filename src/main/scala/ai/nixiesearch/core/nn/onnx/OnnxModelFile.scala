package ai.nixiesearch.core.nn.onnx

import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Logging
import cats.effect.IO
import io.circe.{Decoder, Encoder, Json}

case class OnnxModelFile(base: String, data: Option[String] = None)

object OnnxModelFile extends Logging {
  given onnxModelFileEncoder: Encoder[OnnxModelFile] = Encoder.instance {
    case OnnxModelFile(base, None)       => Json.fromString(base)
    case OnnxModelFile(base, Some(data)) =>
      Json.obj("base" -> Json.fromString(base), "data" -> Json.fromString(data))
  }

  given onnxModelDecoder: Decoder[OnnxModelFile] = Decoder.instance(c =>
    c.as[String] match {
      case Right(value) => Right(OnnxModelFile(value))
      case Left(_)      =>
        for {
          base <- c.downField("base").as[String]
          data <- c.downField("data").as[Option[String]]
        } yield {
          OnnxModelFile(base, data)
        }
    }
  )

  def chooseModelFile(files: List[String], forced: Option[OnnxModelFile]): IO[OnnxModelFile] = {
    forced match {
      case Some(f) => IO.pure(f)
      case None    =>
        files.filter(_.endsWith(".onnx")) match {
          case Nil           => IO.raiseError(UserError(s"no ONNX files found in the repo. files=$files"))
          case base :: other =>
            if (other.nonEmpty) {
              logger.warn(s"multiple ONNX files found in the repo: choosing $base (and ignoring $other)")
              logger.warn(
                "If you want to use another ONNX file, please set inference.embedding.<name>.file with desired file name"
              )
            }
            val dataFile = s"${base}_data"
            if (files.contains(dataFile)) {
              IO.pure(OnnxModelFile(base, Some(dataFile)))
            } else {
              IO.pure(OnnxModelFile(base))
            }
        }
    }

  }

}

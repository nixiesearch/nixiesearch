package ai.nixiesearch.core.nn.model.ranking.providers

import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.onnx.OnnxModelFile
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

case class OnnxRankModel()

object OnnxRankModel {
  case class OnnxRankInferenceModelConfig(
      model: ModelHandle,
      file: Option[OnnxModelFile] = None,
      maxTokens: Int = 512,
      batchSize: Int = 32
  )

  given onnxRankConfigEncoder: Encoder[OnnxRankInferenceModelConfig] = deriveEncoder

  given onnxRankConfigDecoder: Decoder[OnnxRankInferenceModelConfig] = Decoder.instance(c =>
    for {
      model     <- c.downField("model").as[ModelHandle]
      file      <- c.downField("file").as[Option[OnnxModelFile]]
      seqlen    <- c.downField("max_tokens").as[Option[Int]]
      batchSize <- c.downField("batch_size").as[Option[Int]]
    } yield {
      OnnxRankInferenceModelConfig(
        model,
        file = file,
        maxTokens = seqlen.getOrElse(512),
        batchSize = batchSize.getOrElse(32)
      )
    }
  )
}

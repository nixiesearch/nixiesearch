package ai.nixiesearch.config

import ai.nixiesearch.config.InferenceConfig.InferenceModelConfig
import ai.nixiesearch.core.nn.ModelHandle
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

case class InferenceConfig(embedding: Map[String, InferenceModelConfig], generative: Map[String, InferenceModelConfig])

object InferenceConfig {
  case class InferenceModelConfig(handle: ModelHandle, file: Option[String] = None, gpu: Boolean = false)
  given inferenceModelConfigEncoder: Encoder[InferenceModelConfig] = deriveEncoder
  given inferenceModelCondifDecoder: Decoder[InferenceModelConfig] = Decoder.instance(c =>
    for {
      handle <- c.downField("handle").as[ModelHandle]
      file   <- c.downField("file").as[Option[String]]
      gpu    <- c.downField("gpu").as[Option[Boolean]]
    } yield {
      InferenceModelConfig(handle, file, gpu.getOrElse(false))
    }
  )
  given inferenceConfigEncoder: Encoder[InferenceConfig] = deriveEncoder
}

package ai.nixiesearch.config

import ai.nixiesearch.config.InferenceConfig.GenInferenceModelConfig.LLMPromptTemplate
import ai.nixiesearch.config.InferenceConfig.{EmbeddingInferenceModelConfig, GenInferenceModelConfig}
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.*

import scala.util.Success

case class InferenceConfig(
    embedding: Map[ModelRef, EmbeddingInferenceModelConfig] = Map.empty,
    generative: Map[ModelRef, GenInferenceModelConfig] = Map.empty
)

object InferenceConfig {
  case class PromptConfig(doc: String = "", query: String = "")
  given promptEncoder: Encoder[PromptConfig] = deriveEncoder
  given promptDecoder: Decoder[PromptConfig] = Decoder.instance(c =>
    for {
      doc   <- c.downField("doc").as[Option[String]]
      query <- c.downField("query").as[Option[String]]
    } yield {
      PromptConfig(doc.getOrElse(""), query.getOrElse(""))
    }
  )

  sealed trait EmbeddingInferenceModelConfig

  object EmbeddingInferenceModelConfig {
    case class OnnxEmbeddingInferenceModelConfig(
        model: ModelHandle,
        file: Option[String] = None,
        prompt: PromptConfig = PromptConfig(),
        seqlen: Int = 512
    ) extends EmbeddingInferenceModelConfig

    case class OpenAIEmbeddingInferenceModelConfig(model: String) extends EmbeddingInferenceModelConfig

    given onnxEmbeddingConfigEncoder: Encoder[OnnxEmbeddingInferenceModelConfig] = deriveEncoder
    given onnxEmbeddingConfigDecoder: Decoder[OnnxEmbeddingInferenceModelConfig] = Decoder.instance(c =>
      for {
        model  <- c.downField("model").as[ModelHandle]
        file   <- c.downField("file").as[Option[String]]
        seqlen <- c.downField("seqlen").as[Option[Int]]
        prompt <- c.downField("prompt").as[Option[PromptConfig]]
      } yield {
        OnnxEmbeddingInferenceModelConfig(
          model,
          file,
          prompt.getOrElse(PromptConfig()),
          seqlen.getOrElse(512)
        )
      }
    )

    given openAIEmbeddingConfigEncoder: Encoder[OpenAIEmbeddingInferenceModelConfig] = deriveEncoder
    given openAIEmbeddingConfigDecoder: Decoder[OpenAIEmbeddingInferenceModelConfig] = deriveDecoder

    given embedInferenceModelConfigEncoder: Encoder[EmbeddingInferenceModelConfig] = Encoder.instance {
      case e: OnnxEmbeddingInferenceModelConfig =>
        Json.obj("provider" -> Json.fromString("onnx")).deepMerge(onnxEmbeddingConfigEncoder(e))
      case e: OpenAIEmbeddingInferenceModelConfig =>
        Json.obj("provider" -> Json.fromString("openai")).deepMerge(openAIEmbeddingConfigEncoder(e))
    }

    given embedInferenceModelConfigDecoder: Decoder[EmbeddingInferenceModelConfig] = Decoder.instance(c =>
      c.downField("provider").as[String] match {
        case Left(err)       => Left(err)
        case Right("onnx")   => onnxEmbeddingConfigDecoder.tryDecode(c)
        case Right("openai") => openAIEmbeddingConfigDecoder.tryDecode(c)
        case Right(other)    => Left(DecodingFailure(s"provider $other not supported", c.history))
      }
    )

  }

  case class GenInferenceModelConfig(
      model: ModelHandle,
      prompt: LLMPromptTemplate,
      system: Option[String] = None,
      file: Option[String] = None
  )
  object GenInferenceModelConfig {

    sealed trait LLMPromptTemplate {
      def template: String
      def build(user: String, system: Option[String] = None): String = system match {
        case None      => template.replace("{user}", user)
        case Some(sys) => template.replace("{system}", sys).replace("{user}", user)
      }
    }
    object LLMPromptTemplate {
      case class RawTemplate(template: String) extends LLMPromptTemplate
      case object Qwen2Template extends LLMPromptTemplate {
        override val template: String = s"""<|im_start|>user\n{user}<|im_end|>\n<|im_start|>assistant\n"""
      }
      case object Llama3Template extends LLMPromptTemplate {
        override val template = s"""<|start_header_id|>system<|end_header_id|>
                                   |
                                   |{system}<|eot_id|><|start_header_id|>user<|end_header_id|>
                                   |
                                   |{user}<|eot_id|><|start_header_id|>assistant<|end_header_id|>
                                   |
                                   |""".stripMargin
      }

      given promptTemplateEncoder: Encoder[LLMPromptTemplate] = Encoder.instance {
        case RawTemplate(template) => Json.fromString(template)
        case Llama3Template        => Json.fromString("llama3")
        case Qwen2Template         => Json.fromString("qwen2")
      }

      given promptTemplateDecoder: Decoder[LLMPromptTemplate] = Decoder.decodeString.emapTry {
        case "llama3" => Success(Llama3Template)
        case "qwen2"  => Success(Qwen2Template)
        case other    => Success(RawTemplate(other))
      }
    }

    given genInferenceModelConfigEncoder: Encoder[GenInferenceModelConfig] = deriveEncoder

    given genInferenceModelConfigDecoder: Decoder[GenInferenceModelConfig] = Decoder.instance(c =>
      for {
        model  <- c.downField("model").as[ModelHandle]
        file   <- c.downField("file").as[Option[String]]
        prompt <- c.downField("prompt").as[LLMPromptTemplate]
        system <- c.downField("system").as[Option[String]]
      } yield {
        GenInferenceModelConfig(model, prompt, system, file)
      }
    )
  }

  given inferenceConfigEncoder: Encoder[InferenceConfig] = deriveEncoder[InferenceConfig]
  given inferenceConfigDecoder: Decoder[InferenceConfig] = Decoder.instance(c =>
    for {
      embed <- c.downField("embedding").as[Option[Map[ModelRef, EmbeddingInferenceModelConfig]]]
      gen   <- c.downField("generative").as[Option[Map[ModelRef, GenInferenceModelConfig]]]
    } yield {
      InferenceConfig(embedding = embed.getOrElse(Map.empty), generative = gen.getOrElse(Map.empty))
    }
  )
}

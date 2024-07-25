package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.mapping.RAGConfig.RAGModelConfig
import ai.nixiesearch.core.nn.ModelHandle
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.*
import scala.util.Success

case class RAGConfig(models: List[RAGModelConfig] = Nil)

object RAGConfig {
  case class RAGModelConfig(handle: ModelHandle, prompt: PromptTemplate, name: String, system: Option[String] = None)

  sealed trait PromptTemplate {
    def template: String
    def build(user: String, system: Option[String] = None): String = system match {
      case None      => template.replace("{user}", user)
      case Some(sys) => template.replace("{system}", sys).replace("{user}", user)
    }
  }
  object PromptTemplate {
    case class RawTemplate(template: String) extends PromptTemplate
    case object Qwen2Template extends PromptTemplate {
      override val template: String = s"""<|im_start|>user\n{user}<|im_end|>\n<|im_start|>assistant\n"""
    }
    case object Llama3Template extends PromptTemplate {
      override val template = s"""<|start_header_id|>system<|end_header_id|>
                                 |
                                 |{system}<|eot_id|><|start_header_id|>user<|end_header_id|>
                                 |
                                 |{user}<|eot_id|><|start_header_id|>assistant<|end_header_id|>
                                 |
                                 |""".stripMargin
    }

    given promptTemplateEncoder: Encoder[PromptTemplate] = Encoder.instance {
      case RawTemplate(template) => Json.fromString(template)
      case Llama3Template        => Json.fromString("llama3")
      case Qwen2Template         => Json.fromString("qwen2")
    }

    given promptTemplateDecoder: Decoder[PromptTemplate] = Decoder.decodeString.emapTry {
      case "llama3" => Success(Llama3Template)
      case "qwen2"  => Success(Qwen2Template)
      case other    => Success(RawTemplate(other))
    }
  }

  given ragModelEncoder: Encoder[RAGModelConfig] = deriveEncoder
  given ragConfigEncoder: Encoder[RAGConfig]     = deriveEncoder

  given ragModelDecoder: Decoder[RAGModelConfig] = Decoder.instance(c =>
    for {
      handle <- c.downField("handle").as[ModelHandle]
      prompt <- c.downField("prompt").as[PromptTemplate]
      name   <- c.downField("name").as[String]
      system <- c.downField("system").as[Option[String]]
    } yield {
      RAGModelConfig(handle, prompt, name, system)
    }
  )

  given ragConfigDecoder: Decoder[RAGConfig] = Decoder.instance(c =>
    for {
      models <- c.downField("models").as[Option[List[RAGModelConfig]]]
    } yield {
      RAGConfig(models.getOrElse(Nil))
    }
  )
}

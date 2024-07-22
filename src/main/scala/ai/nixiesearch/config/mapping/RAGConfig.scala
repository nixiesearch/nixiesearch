package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.mapping.RAGConfig.RAGModel
import ai.nixiesearch.core.nn.ModelHandle
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.*
import scala.util.Success

case class RAGConfig(models: List[RAGModel] = Nil)

object RAGConfig {
  case class RAGModel(handle: ModelHandle, prompt: PromptTemplate, name: String, system: Option[String] = None)

  sealed trait PromptTemplate {
    def template: String
  }
  object PromptTemplate {
    case class RawTemplate(template: String) extends PromptTemplate
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
    }

    given promptTemplateDecoder: Decoder[PromptTemplate] = Decoder.decodeString.emapTry {
      case "llama3" => Success(Llama3Template)
      case other    => Success(RawTemplate(other))
    }
  }

  given ragModelEncoder: Encoder[RAGModel]   = deriveEncoder
  given ragConfigEncoder: Encoder[RAGConfig] = deriveEncoder

  given ragModelDecoder: Decoder[RAGModel] = Decoder.instance(c =>
    for {
      handle <- c.downField("handle").as[ModelHandle]
      prompt <- c.downField("prompt").as[PromptTemplate]
      name   <- c.downField("name").as[String]
      system <- c.downField("system").as[Option[String]]
    } yield {
      RAGModel(handle, prompt, name, system)
    }
  )

  given ragConfigDecoder: Decoder[RAGConfig] = Decoder.instance(c =>
    for {
      models <- c.downField("models").as[Option[List[RAGModel]]]
    } yield {
      RAGConfig(models.getOrElse(Nil))
    }
  )
}

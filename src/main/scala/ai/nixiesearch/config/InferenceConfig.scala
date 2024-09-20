package ai.nixiesearch.config

import ai.nixiesearch.config.InferenceConfig.CompletionInferenceModelConfig.LLMPromptTemplate
import ai.nixiesearch.config.InferenceConfig.{EmbeddingInferenceModelConfig, CompletionInferenceModelConfig}
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.*

import scala.util.Success

case class InferenceConfig(
    embedding: Map[ModelRef, EmbeddingInferenceModelConfig] = Map.empty,
    completion: Map[ModelRef, CompletionInferenceModelConfig] = Map.empty
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
        maxTokens: Int = 512,
        batchSize: Int = 32
    ) extends EmbeddingInferenceModelConfig

    case class OpenAIEmbeddingInferenceModelConfig(model: String) extends EmbeddingInferenceModelConfig

    given onnxEmbeddingConfigEncoder: Encoder[OnnxEmbeddingInferenceModelConfig] = deriveEncoder
    given onnxEmbeddingConfigDecoder: Decoder[OnnxEmbeddingInferenceModelConfig] = Decoder.instance(c =>
      for {
        model     <- c.downField("model").as[ModelHandle]
        file      <- c.downField("file").as[Option[String]]
        seqlen    <- c.downField("max_tokens").as[Option[Int]]
        prompt    <- c.downField("prompt").as[Option[PromptConfig]]
        batchSize <- c.downField("batch_size").as[Option[Int]]
      } yield {
        OnnxEmbeddingInferenceModelConfig(
          model,
          file,
          prompt.getOrElse(PromptConfig()),
          maxTokens = seqlen.getOrElse(512),
          batchSize = batchSize.getOrElse(32)
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

  sealed trait CompletionInferenceModelConfig

  object CompletionInferenceModelConfig {
    case class LlamacppInferenceModelConfig(
        model: ModelHandle,
        prompt: LLMPromptTemplate,
        system: Option[String] = None,
        file: Option[String] = None,
        options: LlamacppParams = LlamacppParams()
    ) extends CompletionInferenceModelConfig

    case class LlamacppParams(
        n_threads: Int = Runtime.getRuntime.availableProcessors(),
        n_gpu_layers: Int = 100,
        n_parallel: Int = 8,
        cont_batching: Boolean = true,
        flash_attn: Boolean = true,
        use_mmap: Boolean = true,
        use_mlock: Boolean = true,
        no_kv_offload: Boolean = false,
        seed: Int = 42
    )

    object LlamacppParams {
      given llamaCppParamsEncoder: Encoder[LlamacppParams] = deriveEncoder
      given llamaCppParamsDecoder: Decoder[LlamacppParams] = Decoder.instance(c =>
        for {
          n_threads     <- c.downField("n_threads").as[Option[Int]]
          n_gpu_layers  <- c.downField("n_gpu_layers").as[Option[Int]]
          n_parallel    <- c.downField("n_parallel").as[Option[Int]]
          cont_batching <- c.downField("cont_batching").as[Option[Boolean]]
          flash_attn    <- c.downField("flash_attn").as[Option[Boolean]]
          use_mmap      <- c.downField("use_mmap").as[Option[Boolean]]
          use_mlock     <- c.downField("use_mlock").as[Option[Boolean]]
          no_kv_offload <- c.downField("no_kv_offload").as[Option[Boolean]]
          seed          <- c.downField("seed").as[Option[Int]]
        } yield {
          val d = LlamacppParams()
          LlamacppParams(
            n_threads.getOrElse(d.n_threads),
            n_gpu_layers.getOrElse(d.n_gpu_layers),
            n_parallel.getOrElse(d.n_parallel),
            cont_batching.getOrElse(d.cont_batching),
            flash_attn.getOrElse(d.flash_attn),
            use_mmap.getOrElse(d.use_mmap),
            use_mlock.getOrElse(d.use_mlock),
            no_kv_offload.getOrElse(d.no_kv_offload),
            seed.getOrElse(d.seed)
          )
        }
      )
    }

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

    given llamacppInferenceModelConfigEncoder: Encoder[LlamacppInferenceModelConfig] = deriveEncoder

    given llamacppInferenceModelConfigDecoder: Decoder[LlamacppInferenceModelConfig] = Decoder.instance(c =>
      for {
        model   <- c.downField("model").as[ModelHandle]
        file    <- c.downField("file").as[Option[String]]
        prompt  <- c.downField("prompt").as[LLMPromptTemplate]
        system  <- c.downField("system").as[Option[String]]
        options <- c.downField("options").as[Option[LlamacppParams]]
      } yield {
        LlamacppInferenceModelConfig(model, prompt, system, file, options = options.getOrElse(LlamacppParams()))
      }
    )

    given completionInferenceModelConfigEncoder: Encoder[CompletionInferenceModelConfig] = Encoder.instance {
      case llama: LlamacppInferenceModelConfig =>
        Json.obj("provider" -> Json.fromString("llamacpp")).deepMerge(llamacppInferenceModelConfigEncoder(llama))
    }

    given completionInferenceModelConfigDecoder: Decoder[CompletionInferenceModelConfig] = Decoder.instance(c =>
      c.downField("provider").as[String] match {
        case Left(err)         => Left(err)
        case Right("llamacpp") => llamacppInferenceModelConfigDecoder.tryDecode(c)
        case Right(other) =>
          Left(DecodingFailure(s"completion provider '$other' not supported yet. Maybe try 'llamacpp'?", c.history))
      }
    )
  }

  given inferenceConfigEncoder: Encoder[InferenceConfig] = deriveEncoder[InferenceConfig]
  given inferenceConfigDecoder: Decoder[InferenceConfig] = Decoder.instance(c =>
    for {
      embed <- c.downField("embedding").as[Option[Map[ModelRef, EmbeddingInferenceModelConfig]]]
      gen   <- c.downField("completion").as[Option[Map[ModelRef, CompletionInferenceModelConfig]]]
    } yield {
      InferenceConfig(embedding = embed.getOrElse(Map.empty), completion = gen.getOrElse(Map.empty))
    }
  )
}

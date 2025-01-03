package ai.nixiesearch.config

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
        system: Option[String] = None,
        file: Option[String] = None,
        options: LlamacppParams = LlamacppParams()
    ) extends CompletionInferenceModelConfig

    case class LlamacppParams(
        threads: Int = Runtime.getRuntime.availableProcessors(),
        gpu_layers: Int = 1000,
        cont_batching: Boolean = true,
        flash_attn: Boolean = true,
        seed: Int = 42
    )

    object LlamacppParams {
      given llamaCppParamsEncoder: Encoder[LlamacppParams] = deriveEncoder
      given llamaCppParamsDecoder: Decoder[LlamacppParams] = Decoder.instance(c =>
        for {
          threads       <- c.downField("threads").as[Option[Int]]
          gpu_layers    <- c.downField("gpu_layers").as[Option[Int]]
          cont_batching <- c.downField("cont_batching").as[Option[Boolean]]
          flash_attn    <- c.downField("flash_attn").as[Option[Boolean]]
          seed          <- c.downField("seed").as[Option[Int]]
        } yield {
          val d = LlamacppParams()
          LlamacppParams(
            threads.getOrElse(d.threads),
            gpu_layers.getOrElse(d.gpu_layers),
            cont_batching.getOrElse(d.cont_batching),
            flash_attn.getOrElse(d.flash_attn),
            seed.getOrElse(d.seed)
          )
        }
      )
    }

    given llamacppInferenceModelConfigEncoder: Encoder[LlamacppInferenceModelConfig] = deriveEncoder

    given llamacppInferenceModelConfigDecoder: Decoder[LlamacppInferenceModelConfig] = Decoder.instance(c =>
      for {
        model   <- c.downField("model").as[ModelHandle]
        file    <- c.downField("file").as[Option[String]]
        system  <- c.downField("system").as[Option[String]]
        options <- c.downField("options").as[Option[LlamacppParams]]
      } yield {
        LlamacppInferenceModelConfig(model, system, file, options = options.getOrElse(LlamacppParams()))
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

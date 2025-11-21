package ai.nixiesearch.config

import ai.nixiesearch.config.InferenceConfig.{
  CompletionInferenceModelConfig,
  EmbeddingInferenceModelConfig,
  RankInferenceModelConfig
}
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.nixiesearch.core.nn.model.embedding.providers.CohereEmbedModel.{
  CohereEmbeddingInferenceModelConfig,
  cohereEmbeddingConfigDecoder,
  cohereEmbeddingConfigEncoder
}
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel.{
  OnnxEmbeddingInferenceModelConfig,
  onnxEmbeddingConfigDecoder,
  onnxEmbeddingConfigEncoder
}
import ai.nixiesearch.core.nn.model.embedding.providers.OpenAIEmbedModel
import ai.nixiesearch.core.nn.model.embedding.providers.OpenAIEmbedModel.{
  OpenAIEmbeddingInferenceModelConfig,
  openAIEmbeddingConfigDecoder,
  openAIEmbeddingConfigEncoder
}
import ai.nixiesearch.core.nn.model.ranking.providers.OnnxRankModel.{
  OnnxRankInferenceModelConfig,
  onnxRankConfigDecoder,
  onnxRankConfigEncoder
}
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.*

import scala.util.{Failure, Success}

case class InferenceConfig(
    embedding: Map[ModelRef, EmbeddingInferenceModelConfig] = Map.empty,
    completion: Map[ModelRef, CompletionInferenceModelConfig] = Map.empty,
    ranker: Map[ModelRef, RankInferenceModelConfig] = Map.empty
) {
  def hasLocalModels: Boolean = {
    embedding.values.exists(_.isLocal) || completion.values.exists(_.isLocal) || ranker.values.exists(_.isLocal)
  }
}

object InferenceConfig {
  case class PromptConfig(doc: String = "", query: String = "")
  object PromptConfig extends Logging {
    val E5                                      = PromptConfig("passage: ", "query: ")
    def apply(model: ModelHandle): PromptConfig = {
      model match {
        case hf: HuggingFaceHandle =>
          hf match {
            case HuggingFaceHandle("nixiesearch", name) if name.contains("e5") => E5
            case HuggingFaceHandle("intfloat", _)                              => E5
            case HuggingFaceHandle("Snowflake", _)                             => PromptConfig(query = "query: ")
            case HuggingFaceHandle("BAAI", "bge-m3")                           => PromptConfig()
            case HuggingFaceHandle("BAAI", x) if x.startsWith("bge") && x.contains("-zh-") =>
              PromptConfig(query = "为这个句子生成表示以用于检索相关文章：")
            case HuggingFaceHandle("BAAI", x) if x.startsWith("bge") && x.contains("-en-") =>
              PromptConfig(query = "Represent this sentence for searching relevant passages: ")
            case HuggingFaceHandle(_, "UAE-Large-V1") =>
              PromptConfig(query = "Represent this sentence for searching relevant passages: ")
            case HuggingFaceHandle("mixedbread-ai", "mxbai-embed-large-v1") =>
              PromptConfig(query = "Represent this sentence for searching relevant passages: ")
            case HuggingFaceHandle("mixedbread-ai", "deepset-mxbai-embed-de-large-v1") =>
              PromptConfig("passage: ", "query: ")
            case HuggingFaceHandle("Alibaba-NLP", _) =>
              PromptConfig()
            case HuggingFaceHandle("jinaai", name) if name.contains("v3") =>
              PromptConfig(
                query = "Represent the query for retrieving evidence documents: ",
                doc = "Represent the document for retrieval: "
              )
            case HuggingFaceHandle("nomic-ai", _) =>
              PromptConfig(
                query = "search_document: ",
                doc = "search_query: "
              )
            case HuggingFaceHandle("infly", _) =>
              PromptConfig(
                query = "Instruct: Given a web search query, retrieve relevant passages that answer the query\\nQuery: "
              )
            case HuggingFaceHandle("NovaSearch", _) =>
              PromptConfig(
                query =
                  "Instruct: Given a web search query, retrieve relevant passages that answer the query.\\nQuery: "
              )
            case HuggingFaceHandle("Qwen", _) =>
              PromptConfig(
                query = "Instruct: Given a query, retrieve relevant passages that answer the query.\\nQuery: ",
                doc = ""
              )
            case HuggingFaceHandle(_, name) if name.toLowerCase.contains("qwen") =>
              PromptConfig(
                query = "Instruct: Given a query, retrieve relevant passages that answer the query.\\nQuery: ",
                doc = ""
              )
            case _ => PromptConfig()
          }
        case LocalModelHandle(_) =>
          logger.warn("Loading embedding model from disk, cannot guess doc/query prompts based on the model name.")
          logger.warn(
            "Using empty prompts. Please set them explicitly in the config at inference.embedding.<model>.prompt."
          )
          PromptConfig()
      }
    }

    given promptEncoder: Encoder[PromptConfig] = deriveEncoder
    given promptDecoder: Decoder[PromptConfig] = Decoder.instance(c =>
      for {
        doc   <- c.downField("doc").as[Option[String]]
        query <- c.downField("query").as[Option[String]]
      } yield {
        PromptConfig(doc.getOrElse(""), query.getOrElse(""))
      }
    )
  }

  trait EmbeddingInferenceModelConfig {
    def cache: EmbedCacheConfig
    def isLocal: Boolean
  }

  object EmbeddingInferenceModelConfig extends Logging {

    given embedInferenceModelConfigEncoder: Encoder[EmbeddingInferenceModelConfig] = Encoder.instance {
      case e: OnnxEmbeddingInferenceModelConfig =>
        Json.obj("provider" -> Json.fromString("onnx")).deepMerge(onnxEmbeddingConfigEncoder(e))
      case e: OpenAIEmbeddingInferenceModelConfig =>
        Json.obj("provider" -> Json.fromString("openai")).deepMerge(openAIEmbeddingConfigEncoder(e))
      case e: CohereEmbeddingInferenceModelConfig =>
        Json.obj("provider" -> Json.fromString("cohere")).deepMerge(cohereEmbeddingConfigEncoder(e))
    }

    given embedInferenceModelConfigDecoder: Decoder[EmbeddingInferenceModelConfig] = Decoder.instance(c =>
      c.downField("provider").as[Option[String]] match {
        case Left(err)             => Left(err)
        case Right(Some("onnx"))   => onnxEmbeddingConfigDecoder.tryDecode(c)
        case Right(Some("openai")) => openAIEmbeddingConfigDecoder.tryDecode(c)
        case Right(Some("cohere")) => cohereEmbeddingConfigDecoder.tryDecode(c)
        case Right(None)           =>
          c.downField("model").as[Option[String]] match {
            case Left(err)                                                               => Left(err)
            case Right(Some(model)) if OpenAIEmbedModel.SUPPORTED_MODELS.contains(model) =>
              logger.debug(
                s"Model $model appears to be an OpenAI model (override with 'provider: <name>' if detection is incorrect)"
              )
              openAIEmbeddingConfigDecoder.tryDecode(c)
            case Right(_) => onnxEmbeddingConfigDecoder.tryDecode(c)
          }

        case Right(other) => Left(DecodingFailure(s"provider $other not supported", c.history))
      }
    )

  }

  sealed trait CompletionInferenceModelConfig {
    def isLocal: Boolean
  }

  object CompletionInferenceModelConfig {
    case class LlamacppInferenceModelConfig(
        model: ModelHandle,
        system: Option[String] = None,
        file: Option[String] = None,
        options: LlamacppParams = LlamacppParams()
    ) extends CompletionInferenceModelConfig {
      override def isLocal: Boolean = true
    }

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
        case Right(other)      =>
          Left(DecodingFailure(s"completion provider '$other' not supported yet. Maybe try 'llamacpp'?", c.history))
      }
    )
  }

  case class RankPromptConfig(
      template: Option[String] = None
  ) {
    def format(query: String, document: String): String = {
      template match {
        case Some(tmpl) =>
          tmpl
            .replace("{query}", query)
            .replace("{document}", document)
        case None =>
          "" // No template - will use default pair encoding
      }
    }
  }
  object RankPromptConfig extends Logging {
    val Qwen3Reranker = RankPromptConfig(
      template = Some(
        "<|im_start|>system\nJudge whether the Document meets the requirements based on the Query and the Instruct provided. Note that the answer can only be \"yes\" or \"no\".<|im_end|>\n<|im_start|>user\n<Instruct>: Given a web search query, retrieve relevant passages that answer the query\n<Query>: {query}\n<Document>: {document}<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n"
      )
    )

    def apply(model: ModelHandle): RankPromptConfig = {
      model match {
        case hf: HuggingFaceHandle =>
          val modelName = hf.name.toLowerCase
          if (modelName.contains("qwen") && modelName.contains("rerank")) {
            Qwen3Reranker
          } else {
            RankPromptConfig() // Default: no prompt formatting
          }
        case LocalModelHandle(_) =>
          logger.warn("Loading ranker model from disk, cannot guess prompt template.")
          logger.warn("Using empty prompt. Please set it explicitly in the config at inference.ranker.<model>.prompt.")
          RankPromptConfig()
      }
    }

    given rankPromptEncoder: Encoder[RankPromptConfig] = deriveEncoder
    given rankPromptDecoder: Decoder[RankPromptConfig] = deriveDecoder
  }

  trait RankInferenceModelConfig {
    def model: ModelHandle
    def isLocal: Boolean
  }

  given rankInferenceConfigEncoder: Encoder[RankInferenceModelConfig] = Encoder.instance {
    case e: OnnxRankInferenceModelConfig =>
      Json.obj("provider" -> Json.fromString("onnx")).deepMerge(onnxRankConfigEncoder(e))
  }

  given rankInferenceConfigDecoder: Decoder[RankInferenceModelConfig] = Decoder.instance(c =>
    c.downField("provider").as[Option[String]] match {
      case Right(Some("onnx")) => onnxRankConfigDecoder.tryDecode(c)
      case Right(Some(other))  => Left(DecodingFailure(s"rank provider $other not yet supported", c.history))
      case Right(None)         => Left(DecodingFailure("field 'provider' is required for rank models", c.history))
      case Left(err)           => Left(err)
    }
  )

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

package ai.nixiesearch.config

import ai.nixiesearch.config.InferenceConfig.{CompletionInferenceModelConfig, EmbeddingInferenceModelConfig}
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.*

import scala.util.{Failure, Success}

case class InferenceConfig(
    embedding: Map[ModelRef, EmbeddingInferenceModelConfig] = Map.empty,
    completion: Map[ModelRef, CompletionInferenceModelConfig] = Map.empty
)

object InferenceConfig {
  case class PromptConfig(doc: String = "", query: String = "")
  object PromptConfig extends Logging {
    def apply(model: ModelHandle): PromptConfig = {
      model match {
        case hf: HuggingFaceHandle =>
          hf match {
            case HuggingFaceHandle("nixiesearch", name) if name.contains("e5") => PromptConfig("passage: ", "query: ")
            case HuggingFaceHandle("intfloat", _)                              => PromptConfig("passage: ", "query: ")
            case HuggingFaceHandle("Snowflake", _)                             => PromptConfig(query = "query: ")
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
            case _ => PromptConfig()
          }
        case LocalModelHandle(_) =>
          logger.warn("Loading embedding model from disk, so we cannot guess doc/query prompts based on model name")
          logger.warn(
            "Using empty prompt - please set them explicitly in the config inference.embedding.<model>.prompt"
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

  sealed trait EmbeddingInferenceModelConfig

  object EmbeddingInferenceModelConfig {
    case class OnnxModelFile(base: String, data: Option[String] = None)
    object OnnxModelFile {
      given onnxModelFileEncoder: Encoder[OnnxModelFile] = Encoder.instance {
        case OnnxModelFile(base, None) => Json.fromString(base)
        case OnnxModelFile(base, Some(data)) =>
          Json.obj("base" -> Json.fromString(base), "data" -> Json.fromString(data))
      }

      given onnxModelDecoder: Decoder[OnnxModelFile] = Decoder.instance(c =>
        c.as[String] match {
          case Right(value) => Right(OnnxModelFile(value))
          case Left(_) =>
            for {
              base <- c.downField("base").as[String]
              data <- c.downField("data").as[Option[String]]
            } yield {
              OnnxModelFile(base, data)
            }
        }
      )
    }
    case class OnnxEmbeddingInferenceModelConfig(
        model: ModelHandle,
        file: Option[OnnxModelFile] = None,
        prompt: Option[PromptConfig] = None,
        pooling: Option[PoolingType] = None,
        normalize: Boolean = true,
        maxTokens: Int = 512,
        batchSize: Int = 32
    ) extends EmbeddingInferenceModelConfig

    sealed trait PoolingType
    object PoolingType extends Logging {
      case object MeanPooling extends PoolingType
      case object CLSPooling  extends PoolingType

      def apply(handle: ModelHandle) = handle match {
        case hf: HuggingFaceHandle =>
          hf match {
            case HuggingFaceHandle("Alibaba-NLP", _)   => CLSPooling
            case HuggingFaceHandle("Snowflake", _)     => CLSPooling
            case HuggingFaceHandle("mixedbread-ai", _) => CLSPooling
            case _                                     => MeanPooling
          }
        case LocalModelHandle(dir) =>
          logger.warn("When using local non-HF model, we cannot guess the embedding pooling type")
          logger.warn(
            "Using 'mean' by default, but if you're using GTE/Snowflake embeddings, you need to set inference.embedding.<model>.pooling=cls"
          )
          MeanPooling
      }

      given poolingTypeEncoder: Encoder[PoolingType] = Encoder.encodeString.contramap {
        case MeanPooling => "mean"
        case CLSPooling  => "cls"
      }

      given poolingTypeDecoder: Decoder[PoolingType] = Decoder.decodeString.emapTry {
        case "mean" => Success(MeanPooling)
        case "cls"  => Success(CLSPooling)
        case other  => Failure(UserError(s"only cls/mean pooling types supported, but got '$other'"))
      }
    }

    case class OpenAIEmbeddingInferenceModelConfig(model: String) extends EmbeddingInferenceModelConfig

    given onnxEmbeddingConfigEncoder: Encoder[OnnxEmbeddingInferenceModelConfig] = deriveEncoder
    given onnxEmbeddingConfigDecoder: Decoder[OnnxEmbeddingInferenceModelConfig] = Decoder.instance(c =>
      for {
        model     <- c.downField("model").as[ModelHandle]
        file      <- c.downField("file").as[Option[OnnxModelFile]]
        seqlen    <- c.downField("max_tokens").as[Option[Int]]
        prompt    <- c.downField("prompt").as[Option[PromptConfig]]
        batchSize <- c.downField("batch_size").as[Option[Int]]
        pooling   <- c.downField("pooling").as[Option[PoolingType]]
        normalize <- c.downField("normalize").as[Option[Boolean]]
      } yield {
        OnnxEmbeddingInferenceModelConfig(
          model,
          file = file,
          prompt = prompt,
          maxTokens = seqlen.getOrElse(512),
          batchSize = batchSize.getOrElse(32),
          pooling = pooling,
          normalize = normalize.getOrElse(true)
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
      c.downField("provider").as[Option[String]] match {
        case Left(err)             => Left(err)
        case Right(Some("onnx"))   => onnxEmbeddingConfigDecoder.tryDecode(c)
        case Right(Some("openai")) => openAIEmbeddingConfigDecoder.tryDecode(c)
        case Right(None)           => onnxEmbeddingConfigDecoder.tryDecode(c)
        case Right(other)          => Left(DecodingFailure(s"provider $other not supported", c.history))
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

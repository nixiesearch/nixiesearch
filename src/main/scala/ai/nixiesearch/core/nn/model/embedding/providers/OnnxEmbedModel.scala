package ai.nixiesearch.core.nn.model.embedding.providers

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.nixiesearch.config.EmbedCacheConfig
import ai.nixiesearch.config.EmbedCacheConfig.MemoryCacheConfig
import ai.nixiesearch.config.InferenceConfig.{EmbeddingInferenceModelConfig, PromptConfig}
import ai.nixiesearch.core.Error.{BackendError, UserError}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.nixiesearch.core.nn.huggingface.{HuggingFaceClient, ModelFileCache}
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict.{TransformersConfig, info, logger}
import ai.nixiesearch.core.nn.model.embedding.cache.MemoryCachedEmbedModel
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel.{OnnxEmbeddingInferenceModelConfig, PoolingType}
import ai.nixiesearch.core.nn.model.embedding.providers.OpenAIEmbedModel.OpenAIEmbeddingInferenceModelConfig
import ai.nixiesearch.core.nn.model.embedding.{EmbedModel, EmbedModelDict, EmbedPooling}
import ai.nixiesearch.core.nn.onnx.OnnxConfig.Device
import ai.nixiesearch.core.nn.onnx.{OnnxConfig, OnnxModelFile, OnnxSession}
import ai.nixiesearch.core.nn.onnx.OnnxSession.{CONFIG_FILE, ONNX_THREADS_DEFAULT}
import ai.nixiesearch.util.GPUUtils
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel
import ai.onnxruntime.{OnnxTensor, OrtEnvironment, OrtLoggingLevel, OrtSession}
import cats.effect.kernel.Resource
import cats.effect.{IO, Resource}
import fs2.io.file.Path as Fs2Path
import io.circe.{Decoder, Encoder, Json}
import io.circe.parser.*
import io.circe.generic.semiauto.*

import java.nio.{FloatBuffer, LongBuffer}
import java.nio.file.{Path, Files as NIOFiles}
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success}
import cats.syntax.all.*

case class OnnxEmbedModel(
    env: OrtEnvironment,
    session: OrtSession,
    tokenizer: HuggingFaceTokenizer,
    dim: Int,
    inputTensorNames: List[String],
    modelConfig: TransformersConfig,
    config: OnnxEmbeddingInferenceModelConfig
) extends EmbedModelProvider {
  override val model: String    = config.model.asList.mkString("/")
  override val provider: String = "onnx"
  override val batchSize        = config.batchSize

  private def createPositionIds(batchSize: Long, seqLen: Long): Array[Long] = {
    // np.tile(np.arange(sequence_length), (batch_size, 1))
    val result = new Array[Long](batchSize.toInt * seqLen.toInt)
    var i      = 0
    while (i < batchSize) {
      var j = 0
      while (j < seqLen) {
        result((i * seqLen + j).toInt) = j
        j += 1
      }
      i += 1
    }
    result
  }

  override def encodeBatch(task: TaskType, batch: List[String]): IO[Array[Array[Float]]] = IO {
    val formatted = task match {
      case TaskType.Document => batch.map(doc => config.prompt.doc + doc)
      case TaskType.Query    => batch.map(doc => config.prompt.query + doc)
      case TaskType.Raw      => batch
    }
    val encoded = tokenizer.batchEncode(formatted.toArray)

    val tokens       = encoded.flatMap(e => e.getIds)
    val tokenLengths = encoded.map(e => e.getAttentionMask.sum.toInt)
    val tokenTypes   = encoded.flatMap(e => e.getTypeIds)
    val attMask      = encoded.flatMap(e => e.getAttentionMask)

    val batchSize = batch.length.toLong
    val seqLen    = encoded(0).getIds.length.toLong
    val tensorDim = Array(batchSize, seqLen)

    lazy val positionIds = createPositionIds(batchSize, seqLen)

    val argsList = inputTensorNames.map {
      case "input_ids"      => OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), tensorDim)
      case "token_type_ids" => OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypes), tensorDim)
      case "attention_mask" => OnnxTensor.createTensor(env, LongBuffer.wrap(attMask), tensorDim)
      case "position_ids"   => OnnxTensor.createTensor(env, LongBuffer.wrap(positionIds), tensorDim)
      // for jina-embed-v3
      case "task_id" => OnnxTensor.createTensor(env, LongBuffer.wrap(Array(4L)), Array(1L))
      // KV cache tensors for decoder models like Qwen3
      case name if name.startsWith("past_key_values.") && name.endsWith(".key") =>
        // For GQA models: head_dim = hidden_size / num_kv_heads
        // For MHA models: head_dim = hidden_size / num_attention_heads
        val numKVHeads = modelConfig.num_key_value_heads.getOrElse(modelConfig.num_attention_heads.getOrElse(8))
        val headDim    = dim / numKVHeads
        val kvShape    = Array(batchSize, numKVHeads.toLong, 0L, headDim.toLong)
        OnnxTensor.createTensor(env, FloatBuffer.allocate(0), kvShape)
      case name if name.startsWith("past_key_values.") && name.endsWith(".value") =>
        val numKVHeads = modelConfig.num_key_value_heads.getOrElse(modelConfig.num_attention_heads.getOrElse(8))
        val headDim    = dim / numKVHeads
        val kvShape    = Array(batchSize, numKVHeads.toLong, 0L, headDim.toLong)
        OnnxTensor.createTensor(env, FloatBuffer.allocate(0), kvShape)
      case other => throw Exception(s"input $other not supported")
    }
    val args = inputTensorNames.zip(argsList).toMap
    val result     = session.run(args.asJava)
    val tensor     = result.get(0).getValue.asInstanceOf[Array[Array[Array[Float]]]]
    val normalized = config.pooling match {
      case PoolingType.MeanPooling      => EmbedPooling.mean(tensor, tokenLengths, dim, config.normalize)
      case PoolingType.CLSPooling       => EmbedPooling.cls(tensor, tokenLengths, dim, config.normalize)
      case PoolingType.LastTokenPooling => EmbedPooling.lastToken(tensor, tokenLengths, dim, config.normalize)
    }

    result.close()
    args.values.foreach(_.close())
    normalized
  }

}

object OnnxEmbedModel extends Logging {

  case class OnnxEmbeddingInferenceModelConfig(
      model: ModelHandle,
      pooling: PoolingType,
      prompt: PromptConfig,
      file: Option[OnnxModelFile] = None,
      normalize: Boolean = true,
      maxTokens: Int = 512,
      batchSize: Int = 32,
      cache: EmbedCacheConfig = MemoryCacheConfig(),
      device: Device = Device.CPU(),
      override val paddingSide: Option[OnnxConfig.PaddingSide] = None
  ) extends EmbeddingInferenceModelConfig
      with OnnxConfig
  object OnnxEmbeddingInferenceModelConfig {

    def apply(model: ModelHandle, device: Device) =
      new OnnxEmbeddingInferenceModelConfig(
        model,
        pooling = PoolingType(model),
        prompt = PromptConfig(model),
        device = device
      )

    def apply(model: ModelHandle) =
      new OnnxEmbeddingInferenceModelConfig(
        model,
        pooling = PoolingType(model),
        prompt = PromptConfig(model)
      )
    def apply(model: ModelHandle, file: Option[String]) =
      new OnnxEmbeddingInferenceModelConfig(
        model,
        pooling = PoolingType(model),
        prompt = PromptConfig(model),
        file = file.map(f => OnnxModelFile(f))
      )
  }

  def create(
      handle: ModelHandle,
      conf: OnnxEmbeddingInferenceModelConfig,
      cache: ModelFileCache
  ): Resource[IO, OnnxEmbedModel] = for {
    onnx <- OnnxSession.fromHandle(handle, cache, conf)
  } yield {
    val inputs = onnx.session.getInputNames.asScala.toList
    OnnxEmbedModel(onnx.env, onnx.session, onnx.tokenizer, onnx.config.hidden_size, inputs, onnx.config, conf)
  }

  given onnxEmbeddingConfigEncoder: Encoder[OnnxEmbeddingInferenceModelConfig] = deriveEncoder

  given onnxEmbeddingConfigDecoder: Decoder[OnnxEmbeddingInferenceModelConfig] = Decoder.instance(c =>
    for {
      model       <- c.downField("model").as[ModelHandle]
      file        <- c.downField("file").as[Option[OnnxModelFile]]
      seqlen      <- c.downField("max_tokens").as[Option[Int]]
      prompt      <- c.downField("prompt").as[Option[PromptConfig]]
      batchSize   <- c.downField("batch_size").as[Option[Int]]
      pooling     <- c.downField("pooling").as[Option[PoolingType]]
      normalize   <- c.downField("normalize").as[Option[Boolean]]
      cache       <- c.downField("cache").as[Option[EmbedCacheConfig]]
      device      <- c.downField("device").as[Option[Device]]
      paddingSide <- c.downField("padding_side").as[Option[OnnxConfig.PaddingSide]]
    } yield {
      OnnxEmbeddingInferenceModelConfig(
        model,
        file = file,
        prompt = prompt.getOrElse(PromptConfig(model)),
        maxTokens = seqlen.getOrElse(512),
        batchSize = batchSize.getOrElse(32),
        pooling = pooling.getOrElse(PoolingType(model)),
        normalize = normalize.getOrElse(true),
        cache = cache.getOrElse(MemoryCacheConfig()),
        device = device.getOrElse(Device.CPU()),
        paddingSide = paddingSide
      )
    }
  )

  sealed trait PoolingType

  object PoolingType extends Logging {
    case object MeanPooling extends PoolingType

    case object CLSPooling extends PoolingType

    case object LastTokenPooling extends PoolingType

    def apply(handle: ModelHandle) = handle match {
      case hf: HuggingFaceHandle =>
        hf match {
          case HuggingFaceHandle("Alibaba-NLP", _)   => CLSPooling
          case HuggingFaceHandle("Snowflake", _)     => CLSPooling
          case HuggingFaceHandle("mixedbread-ai", _) => CLSPooling
          case HuggingFaceHandle("Qwen", _)          => LastTokenPooling
          case HuggingFaceHandle(_, name) if name.toLowerCase.contains("qwen") => LastTokenPooling
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
      case MeanPooling      => "mean"
      case CLSPooling       => "cls"
      case LastTokenPooling => "last"
    }

    given poolingTypeDecoder: Decoder[PoolingType] = Decoder.decodeString.emapTry {
      case "mean" => Success(MeanPooling)
      case "cls"  => Success(CLSPooling)
      case "last" => Success(LastTokenPooling)
      case other  => Failure(UserError(s"only cls/mean/last pooling types supported, but got '$other'"))
    }
  }

}

package ai.nixiesearch.core.nn.model.ranking.providers

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.djl.util.PairList
import ai.nixiesearch.config.InferenceConfig.{RankInferenceModelConfig, RankPromptConfig}
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.ModelHandle.HuggingFaceHandle
import ai.nixiesearch.core.nn.huggingface.ModelFileCache
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel.OnnxEmbeddingInferenceModelConfig
import ai.nixiesearch.core.nn.model.ranking.{LogitsProcessor, RankModel}
import ai.nixiesearch.core.nn.model.ranking.providers.OnnxRankModel.OnnxRankInferenceModelConfig
import ai.nixiesearch.core.nn.onnx.OnnxConfig.Device
import ai.nixiesearch.core.nn.onnx.OnnxConfig.Device.CPU
import ai.nixiesearch.core.nn.onnx.{OnnxConfig, OnnxModelFile, OnnxSession}
import ai.onnxruntime.{OnnxTensor, OrtEnvironment, OrtSession}
import cats.effect.{IO, Resource}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

import java.nio.LongBuffer
import scala.jdk.CollectionConverters.*

case class OnnxRankModel(
    env: OrtEnvironment,
    session: OrtSession,
    tokenizer: HuggingFaceTokenizer,
    inputTensorNames: List[String],
    config: OnnxRankInferenceModelConfig
) extends RankModel {
  override val model: String    = config.model.asList.mkString("/")
  override val provider: String = "onnx"
  override val batchSize        = config.batchSize

  override def scoreBatch(query: String, docs: List[String]): IO[List[Float]] = IO {
    val bs = docs.size

    // Format and encode based on prompt configuration
    val encoded = config.prompt.template match {
      case Some(_) =>
        // Template-based formatting (e.g., Qwen3-Reranker)
        val formatted = docs.map(doc => config.prompt.format(query, doc))
        tokenizer.batchEncode(formatted.toArray)
      case None =>
        // Traditional cross-encoder: encode query-document pairs
        tokenizer.batchEncode(new PairList(List.fill(bs)(query).asJava, docs.asJava))
    }

    val tokens     = encoded.flatMap(e => e.getIds)
    val tokenTypes = encoded.flatMap(e => e.getTypeIds)
    val attMask    = encoded.flatMap(e => e.getAttentionMask)

    val tensorDim = Array(bs.toLong, encoded(0).getIds.length)
    val argsList  = inputTensorNames.map {
      case "input_ids"      => OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), tensorDim)
      case "token_type_ids" => OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypes), tensorDim)
      case "attention_mask" => OnnxTensor.createTensor(env, LongBuffer.wrap(attMask), tensorDim)
      case other            => throw Exception(s"input $other not supported")
    }
    val args   = inputTensorNames.zip(argsList).toMap
    val result = session.run(args.asJava)
    val tensor = result.get(0).getValue.asInstanceOf[Array[Array[Float]]]
    val logits = new Array[Float](bs)
    var j      = 0
    while (j < bs) {
      logits(j) = tensor(j)(0)
      j += 1
    }

    result.close()
    args.values.foreach(_.close())

    // Apply logits processing
    val scores = config.logitsProcessor.process(logits)

    scores.toList
  }
}

object OnnxRankModel {
  case class OnnxRankInferenceModelConfig(
      model: ModelHandle,
      file: Option[OnnxModelFile] = None,
      maxTokens: Int = 512,
      batchSize: Int = 32,
      device: Device = CPU(),
      override val paddingSide: Option[OnnxConfig.PaddingSide] = None,
      prompt: RankPromptConfig = RankPromptConfig(),
      logitsProcessor: LogitsProcessor = LogitsProcessor.Noop
  ) extends OnnxConfig
      with RankInferenceModelConfig

  object OnnxRankInferenceModelConfig {
    def apply(model: ModelHandle): OnnxRankInferenceModelConfig = {
      val isQwenReranker = model match {
        case hf: HuggingFaceHandle =>
          val modelName = hf.name.toLowerCase
          modelName.contains("qwen") && modelName.contains("rerank")
        case _ => false
      }

      new OnnxRankInferenceModelConfig(
        model = model,
        paddingSide = if (isQwenReranker) Some(OnnxConfig.PaddingSide.Left) else None,
        prompt = RankPromptConfig(model),
        logitsProcessor = if (isQwenReranker) LogitsProcessor.Sigmoid else LogitsProcessor.Noop
      )
    }

    def apply(model: ModelHandle, device: Device): OnnxRankInferenceModelConfig = {
      val base = apply(model)
      base.copy(device = device)
    }
  }

  def create(
      handle: ModelHandle,
      conf: OnnxRankInferenceModelConfig,
      cache: ModelFileCache
  ): Resource[IO, OnnxRankModel] = for {
    onnx <- OnnxSession.fromHandle(handle, cache, conf)
  } yield {
    val inputs = onnx.session.getInputNames.asScala.toList
    OnnxRankModel(onnx.env, onnx.session, onnx.tokenizer, inputs, conf)
  }

  given onnxRankConfigEncoder: Encoder[OnnxRankInferenceModelConfig] = deriveEncoder

  given onnxRankConfigDecoder: Decoder[OnnxRankInferenceModelConfig] = Decoder.instance(c =>
    for {
      model           <- c.downField("model").as[ModelHandle]
      file            <- c.downField("file").as[Option[OnnxModelFile]]
      seqlen          <- c.downField("max_tokens").as[Option[Int]]
      batchSize       <- c.downField("batch_size").as[Option[Int]]
      device          <- c.downField("device").as[Option[Device]]
      paddingSide     <- c.downField("padding_side").as[Option[OnnxConfig.PaddingSide]]
      prompt          <- c.downField("prompt").as[Option[RankPromptConfig]]
      logitsProcessor <- c.downField("logits_processor").as[Option[LogitsProcessor]]
    } yield {
      val defaults = OnnxRankInferenceModelConfig(model)
      OnnxRankInferenceModelConfig(
        model,
        file = file,
        maxTokens = seqlen.getOrElse(512),
        batchSize = batchSize.getOrElse(32),
        device = device.getOrElse(CPU()),
        paddingSide = paddingSide.orElse(defaults.paddingSide),
        prompt = prompt.getOrElse(defaults.prompt),
        logitsProcessor = logitsProcessor.getOrElse(defaults.logitsProcessor)
      )
    }
  )
}

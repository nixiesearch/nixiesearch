package ai.nixiesearch.core.nn.model.embedding.providers

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.nixiesearch.config.IndexCacheConfig.EmbeddingCacheConfig
import ai.nixiesearch.config.InferenceConfig.{EmbeddingInferenceModelConfig, PromptConfig}
import ai.nixiesearch.core.Error.{BackendError, UserError}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import ai.nixiesearch.core.nn.model.{HuggingFaceClient, ModelFileCache}
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict.{CONFIG_FILE, TransformersConfig, info, logger}
import ai.nixiesearch.core.nn.model.embedding.cache.HeapEmbeddingCache
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel.OnnxEmbeddingInferenceModelConfig.OnnxModelFile
import ai.nixiesearch.core.nn.model.embedding.providers.OnnxEmbedModel.{OnnxEmbeddingInferenceModelConfig, PoolingType}
import ai.nixiesearch.core.nn.model.embedding.providers.OpenAIEmbedModel.OpenAIEmbeddingInferenceModelConfig
import ai.nixiesearch.core.nn.model.embedding.{EmbedModel, EmbedModelDict, EmbedPooling}
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
import java.nio.LongBuffer
import java.nio.file.{Path, Files as NIOFiles}
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success}
import cats.implicits.*

case class OnnxEmbedModel(
    env: OrtEnvironment,
    session: OrtSession,
    tokenizer: HuggingFaceTokenizer,
    dim: Int,
    inputTensorNames: List[String],
    config: OnnxEmbeddingInferenceModelConfig
) extends EmbedModel {
  override def batchSize = config.batchSize

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

    val tensorDim = Array(batch.length.toLong, encoded(0).getIds.length)
    val argsList = inputTensorNames.map {
      case "input_ids"      => OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), tensorDim)
      case "token_type_ids" => OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypes), tensorDim)
      case "attention_mask" => OnnxTensor.createTensor(env, LongBuffer.wrap(attMask), tensorDim)
      // for jina-embed-v3
      case "task_id" => OnnxTensor.createTensor(env, LongBuffer.wrap(Array(4L)), Array(1L))
      case other     => throw Exception(s"input $other not supported")
    }
    val args   = inputTensorNames.zip(argsList).toMap
    val result = session.run(args.asJava)
    val tensor = result.get(0).getValue.asInstanceOf[Array[Array[Array[Float]]]]
    val normalized = config.pooling match {
      case PoolingType.MeanPooling => EmbedPooling.mean(tensor, tokenLengths, dim, config.normalize)
      case PoolingType.CLSPooling  => EmbedPooling.cls(tensor, tokenLengths, dim, config.normalize)
    }

    result.close()
    args.values.foreach(_.close())
    normalized
  }

  override def close(): IO[Unit] = IO {
    logger.debug(s"closing ONNX session $session")
    session.close()
  }
}

object OnnxEmbedModel extends Logging {
  val ONNX_THREADS_DEFAULT = Runtime.getRuntime.availableProcessors()

  case class OnnxEmbeddingInferenceModelConfig(
      model: ModelHandle,
      pooling: PoolingType,
      prompt: PromptConfig,
      file: Option[OnnxModelFile] = None,
      normalize: Boolean = true,
      maxTokens: Int = 512,
      batchSize: Int = 32
  ) extends EmbeddingInferenceModelConfig
  object OnnxEmbeddingInferenceModelConfig {
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

    def apply(model: ModelHandle) =
      new OnnxEmbeddingInferenceModelConfig(model, pooling = PoolingType(model), prompt = PromptConfig(model))
  }



  def createHuggingface(
      handle: HuggingFaceHandle,
      conf: OnnxEmbeddingInferenceModelConfig,
      cache: ModelFileCache
  ): Resource[IO, EmbedModel] = for {
    hf <- HuggingFaceClient.create(cache)
    (model, data, vocab, config) <- Resource.eval(for {
      card      <- hf.model(handle)
      modelFile <- chooseModelFile(card.siblings.map(_.rfilename), conf.file)
      tokenizerFile <- IO.fromOption(card.siblings.map(_.rfilename).find(_ == "tokenizer.json"))(
        BackendError("Cannot find tokenizer.json in repo")
      )
      _         <- info(s"Fetching $handle from HF: model=$modelFile tokenizer=$tokenizerFile")
      modelPath <- hf.getCached(handle, modelFile.base)
      maybeModelDataPath <- modelFile.data match {
        case None       => IO.none
        case Some(data) => hf.getCached(handle, data).map(Option.apply)
      }
      vocabPath <- hf.getCached(handle, tokenizerFile)
      config <- hf
        .getCached(handle, CONFIG_FILE)
        .flatMap(path => IO.fromEither(decode[TransformersConfig](NIOFiles.readString(path))))
    } yield {
      (modelPath, maybeModelDataPath, vocabPath, config)
    })
    onnxEmbedder <- OnnxEmbedModel.create(
      model = model,
      dic = vocab,
      dim = config.hidden_size,
      config = conf
    )
  } yield {
    onnxEmbedder
  }
  def createLocal(handle: LocalModelHandle, conf: OnnxEmbeddingInferenceModelConfig): Resource[IO, EmbedModel] = {
    for {
      (model, modelData, vocab, config) <- Resource.eval(for {
        path      <- IO(Fs2Path(handle.dir))
        files     <- fs2.io.file.Files[IO].list(path).map(_.fileName.toString).compile.toList
        modelFile <- chooseModelFile(files, conf.file)
        tokenizerFile <- IO.fromOption(files.find(_ == "tokenizer.json"))(
          BackendError("cannot find tokenizer.json file in dir")
        )
        _      <- info(s"loading $modelFile from $handle")
        config <- IO.fromEither(decode[TransformersConfig](NIOFiles.readString(path.toNioPath.resolve(CONFIG_FILE))))
      } yield {
        (
          path.toNioPath.resolve(modelFile.base),
          modelFile.data.map(path.toNioPath.resolve),
          path.toNioPath.resolve(tokenizerFile),
          config
        )
      })
      onnxEmbedder <- OnnxEmbedModel.create(
        model = model,
        dic = vocab,
        dim = config.hidden_size,
        config = conf
      )
    } yield {
      onnxEmbedder
    }
  }

  val DEFAULT_MODEL_FILES = Set(
    "model_quantized.onnx",
    "model_opt0_QInt8.onnx",
    "model_opt2_QInt8.onnx",
    "model.onnx"
  )

  val DEFAULT_MODEL_EXT = Set("onnx", "onnx_data")
  def chooseModelFile(files: List[String], forced: Option[OnnxModelFile]): IO[OnnxModelFile] = {
    forced match {
      case Some(f) => IO.pure(f)
      case None =>
        files.filter(_.endsWith(".onnx")) match {
          case Nil => IO.raiseError(UserError(s"no ONNX files found in the repo. files=$files"))
          case base :: other =>
            if (other.nonEmpty) {
              logger.warn(s"multiple ONNX files found in the repo: choosing $base (and ignoring $other)")
              logger.warn(
                "If you want to use another ONNX file, please set inference.embedding.<name>.file with desired file name"
              )
            }
            val dataFile = s"${base}_data"
            if (files.contains(dataFile)) {
              IO.pure(OnnxModelFile(base, Some(dataFile)))
            } else {
              IO.pure(OnnxModelFile(base))
            }
        }
    }

  }

  def create(
      model: Path,
      dic: Path,
      dim: Int,
      threads: Int = ONNX_THREADS_DEFAULT,
      config: OnnxEmbeddingInferenceModelConfig
  ): Resource[IO, OnnxEmbedModel] = for {
    isGPUBuild <- Resource.eval(IO(GPUUtils.isGPUBuild()))
    _          <- Resource.eval(IO.whenA(isGPUBuild)(info(s"Embedding model scheduled for GPU inference")))
    model <- Resource.make(
      IO(createUnsafe(model, dic, dim, isGPUBuild, threads, config))
    )(e => e.close())
  } yield {
    model
  }

  def createUnsafe(
      model: Path,
      dic: Path,
      dim: Int,
      gpu: Boolean = false,
      threads: Int = ONNX_THREADS_DEFAULT,
      config: OnnxEmbeddingInferenceModelConfig
  ) = {
    val tokenizer = HuggingFaceTokenizer.newInstance(
      dic,
      Map("padding" -> "true", "truncation" -> "true", "modelMaxLength" -> config.maxTokens.toString).asJava
    )

    val env  = OrtEnvironment.getEnvironment("sbert")
    val opts = new SessionOptions()
    opts.setIntraOpNumThreads(threads)
    opts.setOptimizationLevel(OptLevel.ALL_OPT)
    if (logger.isDebugEnabled) opts.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_VERBOSE)
    if (gpu) opts.addCUDA(0)

    val session = env.createSession(model.toString, opts)
    val inputs  = session.getInputNames.asScala.toList
    val outputs = session.getOutputNames.asScala.toList
    logger.info(s"Loaded ONNX model (inputs=$inputs outputs=$outputs dim=$dim)")
    OnnxEmbedModel(env, session, tokenizer, dim, inputs, config)
  }

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
        prompt = prompt.getOrElse(PromptConfig(model)),
        maxTokens = seqlen.getOrElse(512),
        batchSize = batchSize.getOrElse(32),
        pooling = pooling.getOrElse(PoolingType(model)),
        normalize = normalize.getOrElse(true)
      )
    }
  )

  sealed trait PoolingType

  object PoolingType extends Logging {
    case object MeanPooling extends PoolingType

    case object CLSPooling extends PoolingType

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

}

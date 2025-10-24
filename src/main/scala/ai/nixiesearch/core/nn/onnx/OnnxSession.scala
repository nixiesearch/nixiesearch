package ai.nixiesearch.core.nn.onnx

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.nixiesearch.core.nn.huggingface.{HuggingFaceClient, ModelFileCache}
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict.TransformersConfig
import ai.nixiesearch.core.nn.onnx.OnnxConfig.Device
import ai.nixiesearch.util.GPUUtils
import ai.onnxruntime.{OrtEnvironment, OrtLoggingLevel, OrtSession}
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel
import cats.effect.IO
import cats.effect.kernel.Resource
import io.circe.parser.*
import fs2.io.file.Path as Fs2Path

import scala.jdk.CollectionConverters.*
import java.nio.file.{Files, Path}

case class OnnxSession(
    env: OrtEnvironment,
    session: OrtSession,
    tokenizer: HuggingFaceTokenizer,
    config: TransformersConfig
)

object OnnxSession extends Logging {
  val ONNX_THREADS_DEFAULT = Runtime.getRuntime.availableProcessors()
  val CONFIG_FILE          = "config.json"

  def fromHandle(
      handle: ModelHandle,
      cache: ModelFileCache,
      config: OnnxConfig
  ): Resource[IO, OnnxSession] = handle match {
    case hf: HuggingFaceHandle => fromHuggingface(hf, cache, config)
    case l: LocalModelHandle   => fromLocal(l, config)
  }

  def fromHuggingface(
      handle: HuggingFaceHandle,
      cache: ModelFileCache,
      onnxConfig: OnnxConfig
  ): Resource[IO, OnnxSession] = for {
    hf                          <- HuggingFaceClient.create(cache)
    (model, vocab, modelConfig) <- Resource.eval(for {
      card          <- hf.model(handle)
      modelFile     <- OnnxModelFile.chooseModelFile(card.siblings.map(_.rfilename), onnxConfig.file)
      tokenizerFile <- IO.fromOption(card.siblings.map(_.rfilename).find(_ == "tokenizer.json"))(
        BackendError("Cannot find tokenizer.json in repo")
      )
      _                  <- info(s"Fetching $handle from HF: model=$modelFile tokenizer=$tokenizerFile")
      modelPath          <- hf.getCached(handle, modelFile.base)
      maybeModelDataPath <- modelFile.data match {
        case None       => IO.none
        case Some(data) => hf.getCached(handle, data).map(Option.apply)
      }
      vocabPath   <- hf.getCached(handle, tokenizerFile)
      modelConfig <- hf
        .getCached(handle, CONFIG_FILE)
        .flatMap(path => IO.fromEither(decode[TransformersConfig](Files.readString(path))))

    } yield {
      (modelPath, vocabPath, modelConfig)
    })
    session <- create(model, vocab, onnxConfig, modelConfig)
  } yield {
    session
  }

  def fromLocal(
      handle: LocalModelHandle,
      onnxConfig: OnnxConfig
  ): Resource[IO, OnnxSession] = {
    for {
      (model, vocab, modelConfig) <- Resource.eval(for {
        path          <- IO(Fs2Path(handle.dir))
        files         <- fs2.io.file.Files[IO].list(path).map(_.fileName.toString).compile.toList
        modelFile     <- OnnxModelFile.chooseModelFile(files, onnxConfig.file)
        tokenizerFile <- IO.fromOption(files.find(_ == "tokenizer.json"))(
          BackendError("cannot find tokenizer.json file in dir")
        )
        _           <- info(s"loading $modelFile from $handle")
        modelConfig <- IO.fromEither(decode[TransformersConfig](Files.readString(path.toNioPath.resolve(CONFIG_FILE))))
      } yield {
        (
          path.toNioPath.resolve(modelFile.base),
          path.toNioPath.resolve(tokenizerFile),
          modelConfig
        )
      })
      session <- create(model, vocab, onnxConfig, modelConfig)
    } yield {
      session
    }
  }

  def create(
      model: Path,
      dic: Path,
      onnxConfig: OnnxConfig,
      modelConfig: TransformersConfig
  ): Resource[IO, OnnxSession] = for {
    session <- Resource.make(IO(createUnsafe(model, dic, onnxConfig, modelConfig)))(sess =>
      info("closing ONNX session") *> IO(sess.session.close())
    )
  } yield {
    session
  }

  def createUnsafe(
      model: Path,
      dic: Path,
      onnxConfig: OnnxConfig,
      modelConfig: TransformersConfig
  ) = {
    val startTime      = System.currentTimeMillis()
    val tokenizerOpts  = Map(
      "padding" -> "true",
      "truncation" -> "true",
      "modelMaxLength" -> onnxConfig.maxTokens.toString
    ) ++ onnxConfig.paddingSide.map(ps => "paddingSide" -> ps.value).toMap

    val tokenizer = HuggingFaceTokenizer.newInstance(dic, tokenizerOpts.asJava)
    val tokenizerFinishTime = System.currentTimeMillis()
    val env                 = OrtEnvironment.getEnvironment("nixiesearch")
    val opts                = new SessionOptions()
    onnxConfig.device match {
      case Device.CPU(threads) =>
        opts.setIntraOpNumThreads(threads)
      case Device.CUDA(id) =>
        opts.addCUDA(id)
    }

    opts.setOptimizationLevel(OptLevel.ALL_OPT)
    if (logger.isDebugEnabled) opts.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_VERBOSE)

    val session           = env.createSession(model.toString, opts)
    val inputs            = session.getInputNames.asScala.toList
    val outputs           = session.getOutputNames.asScala.toList
    val sessionFinishTime = System.currentTimeMillis()
    logger.info(
      s"Loaded ONNX model: inputs=$inputs outputs=$outputs tokenizer=${tokenizerFinishTime - startTime}ms session=${sessionFinishTime - tokenizerFinishTime}ms"
    )
    OnnxSession(env, session, tokenizer, modelConfig)
  }
}

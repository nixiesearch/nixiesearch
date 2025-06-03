package ai.nixiesearch.core.nn.onnx

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.nixiesearch.core.nn.huggingface.{HuggingFaceClient, ModelFileCache}
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict.TransformersConfig
import ai.onnxruntime.{OrtEnvironment, OrtLoggingLevel, OrtSession}
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel
import cats.effect.IO
import cats.effect.kernel.Resource
import io.circe.parser.*
import fs2.io.file.Path as Fs2Path
import scala.jdk.CollectionConverters.*
import java.nio.file.{Files, Path}

case class OnnxSession(env: OrtEnvironment, session: OrtSession, tokenizer: HuggingFaceTokenizer)

object OnnxSession extends Logging {
  val ONNX_THREADS_DEFAULT = Runtime.getRuntime.availableProcessors()
  val CONFIG_FILE          = "config.json"

  def fromHandle(
      handle: ModelHandle,
      file: Option[OnnxModelFile],
      cache: ModelFileCache,
      gpu: Boolean = false,
      threads: Int = ONNX_THREADS_DEFAULT,
      maxTokens: Int = 512
  ): Resource[IO, OnnxSession] = handle match {
    case hf: HuggingFaceHandle => fromHuggingface(hf, file, cache, gpu, threads, maxTokens)
    case l: LocalModelHandle   => fromLocal(l, file, gpu, threads, maxTokens)
  }

  def fromHuggingface(
      handle: HuggingFaceHandle,
      file: Option[OnnxModelFile],
      cache: ModelFileCache,
      gpu: Boolean = false,
      threads: Int = ONNX_THREADS_DEFAULT,
      maxTokens: Int = 512
  ): Resource[IO, OnnxSession] = for {
    hf <- HuggingFaceClient.create(cache)
    (model, vocab, config) <- Resource.eval(for {
      card      <- hf.model(handle)
      modelFile <- OnnxModelFile.chooseModelFile(card.siblings.map(_.rfilename), file)
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
        .flatMap(path => IO.fromEither(decode[TransformersConfig](Files.readString(path))))

    } yield {
      (modelPath, vocabPath, config)
    })
    session <- create(model, vocab, gpu, threads, maxTokens)
  } yield {
    session
  }

  def fromLocal(
      handle: LocalModelHandle,
      file: Option[OnnxModelFile],
      gpu: Boolean = false,
      threads: Int = ONNX_THREADS_DEFAULT,
      maxTokens: Int = 512
  ): Resource[IO, OnnxSession] = {
    for {
      (model, vocab, config) <- Resource.eval(for {
        path      <- IO(Fs2Path(handle.dir))
        files     <- fs2.io.file.Files[IO].list(path).map(_.fileName.toString).compile.toList
        modelFile <- OnnxModelFile.chooseModelFile(files, file)
        tokenizerFile <- IO.fromOption(files.find(_ == "tokenizer.json"))(
          BackendError("cannot find tokenizer.json file in dir")
        )
        _      <- info(s"loading $modelFile from $handle")
        config <- IO.fromEither(decode[TransformersConfig](Files.readString(path.toNioPath.resolve(CONFIG_FILE))))
      } yield {
        (
          path.toNioPath.resolve(modelFile.base),
          path.toNioPath.resolve(tokenizerFile),
          config
        )
      })
      session <- create(model, vocab, gpu, threads, maxTokens)
    } yield {
      session
    }
  }

  def create(
      model: Path,
      dic: Path,
      gpu: Boolean = false,
      threads: Int = ONNX_THREADS_DEFAULT,
      maxTokens: Int = 512
  ): Resource[IO, OnnxSession] =
    Resource.make(IO(createUnsafe(model, dic, gpu, threads, maxTokens)))(sess =>
      info("closing ONNX session") *> IO(sess.session.close())
    )

  def createUnsafe(
      model: Path,
      dic: Path,
      gpu: Boolean = false,
      threads: Int = ONNX_THREADS_DEFAULT,
      maxTokens: Int = 512
  ) = {
    val startTime = System.currentTimeMillis()
    val tokenizer = HuggingFaceTokenizer.newInstance(
      dic,
      Map("padding" -> "true", "truncation" -> "true", "modelMaxLength" -> maxTokens.toString).asJava
    )
    val tokenizerFinishTime = System.currentTimeMillis()
    val env                 = OrtEnvironment.getEnvironment("nixiesearch")
    val opts                = new SessionOptions()
    opts.setIntraOpNumThreads(threads)
    opts.setOptimizationLevel(OptLevel.ALL_OPT)
    if (logger.isDebugEnabled) opts.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_VERBOSE)
    if (gpu) opts.addCUDA(0)

    val session           = env.createSession(model.toString, opts)
    val inputs            = session.getInputNames.asScala.toList
    val outputs           = session.getOutputNames.asScala.toList
    val sessionFinishTime = System.currentTimeMillis()
    logger.info(
      s"Loaded ONNX model: inputs=$inputs outputs=$outputs tokenizer=${tokenizerFinishTime - startTime}ms session=${sessionFinishTime - tokenizerFinishTime}ms"
    )
    OnnxSession(env, session, tokenizer)
  }
}

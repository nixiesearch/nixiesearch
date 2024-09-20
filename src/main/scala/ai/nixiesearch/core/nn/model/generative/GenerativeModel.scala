package ai.nixiesearch.core.nn.model.generative

import ai.nixiesearch.config.InferenceConfig.CompletionInferenceModelConfig
import ai.nixiesearch.config.InferenceConfig.CompletionInferenceModelConfig.{LLMPromptTemplate, LlamacppParams}
import ai.nixiesearch.core.Logging
import cats.effect.IO
import cats.effect.kernel.Resource
import de.kherud.llama.args.LogFormat
import de.kherud.llama.{InferenceParameters, LlamaModel, LogLevel, ModelParameters}
import fs2.Stream

import scala.jdk.CollectionConverters.*
import java.nio.file.Path

trait GenerativeModel {
  def generate(input: String, maxTokens: Int): Stream[IO, String]
}

object GenerativeModel {
  case class LlamacppGenerativeModel(model: LlamaModel, promptTemplate: LLMPromptTemplate)
      extends GenerativeModel
      with Logging {
    override def generate(input: String, maxTokens: Int): Stream[IO, String] = for {
      params   <- Stream(new InferenceParameters(promptTemplate.build(input)).setSeed(0).setNPredict(maxTokens))
      iterator <- Stream.eval(IO(model.generate(params).iterator()))
      frame    <- Stream.fromBlockingIterator[IO](iterator.asScala, 1)
    } yield {
      frame.text
    }
    def close(): IO[Unit] = info("Closing Llamacpp model") *> IO(model.close())
  }

  object LlamacppGenerativeModel extends Logging {
    def create(
        path: Path,
        prompt: LLMPromptTemplate,
        options: LlamacppParams
    ): Resource[IO, LlamacppGenerativeModel] =
      Resource.make(IO(createUnsafe(path, prompt, options)))(_.close())

    def createUnsafe(
        path: Path,
        prompt: LLMPromptTemplate,
        options: LlamacppParams
    ): LlamacppGenerativeModel = {
      LlamaModel.setLogger(LogFormat.TEXT, loggerCallback)
      val params = new ModelParameters()
        .setModelFilePath(path.toString)
        .setNThreads(options.n_threads)
        .setNGpuLayers(options.n_gpu_layers)
        .setUseMmap(options.use_mmap)
        .setUseMmap(options.use_mlock)
        .setNoKvOffload(options.no_kv_offload)
        .setContinuousBatching(options.cont_batching)
        .setNParallel(options.n_parallel)
        .setFlashAttention(options.flash_attn)
        .setSeed(options.seed)
      val model = new LlamaModel(params)
      LlamacppGenerativeModel(model, prompt)
    }

    def loggerCallback(level: LogLevel, message: String): Unit = {
      val noNewline = message.replaceAll("\n", "")
      level match {
        case LogLevel.DEBUG => logger.debug(noNewline)
        case LogLevel.INFO  => logger.info(noNewline)
        case LogLevel.WARN  => logger.warn(noNewline)
        case LogLevel.ERROR => logger.error(noNewline)
      }
    }
  }

}

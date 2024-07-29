package ai.nixiesearch.core.nn.model.generative

import ai.nixiesearch.config.mapping.RAGConfig.{PromptTemplate, RAGModelConfig}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.ModelHandle
import cats.effect.IO
import cats.effect.kernel.Resource
import de.kherud.llama.{InferenceParameters, LlamaModel, ModelParameters}
import fs2.Stream

import scala.jdk.CollectionConverters.*
import java.nio.file.Path

trait GenerativeModel {
  def generate(input: String, maxTokens: Int): Stream[IO, String]
}

object GenerativeModel {
  case class LlamacppGenerativeModel(model: LlamaModel, promptTemplate: PromptTemplate)
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

  object LlamacppGenerativeModel {
    val LLAMACPP_THREADS_DEFAULT = Runtime.getRuntime.availableProcessors()
    def create(
        path: Path,
        prompt: PromptTemplate,
        threads: Int = LLAMACPP_THREADS_DEFAULT
    ): Resource[IO, LlamacppGenerativeModel] =
      Resource.make(IO(createUnsafe(path, prompt, threads)))(_.close())

    def createUnsafe(path: Path, prompt: PromptTemplate, threads: Int = LLAMACPP_THREADS_DEFAULT) = {
      val params = new ModelParameters()
        .setModelFilePath(path.toString)
        .setNThreads(threads)
      val model = new LlamaModel(params)
      LlamacppGenerativeModel(model, prompt)
    }
  }

  def create(config: RAGModelConfig): Resource[IO, GenerativeModel] = ???
}

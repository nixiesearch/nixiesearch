package ai.nixiesearch.core.nn.model.generative

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
  case class LlamacppGenerativeModel(model: LlamaModel, promptTemplate: String) extends GenerativeModel {
    override def generate(input: String, maxTokens: Int): Stream[IO, String] = {
      val params = new InferenceParameters(promptTemplate)
      Stream.fromBlockingIterator[IO](model.generate(params).iterator().asScala, 1).map(out => out.text)
    }
    def close(): IO[Unit] = IO(model.close())
  }

  object LlamacppGenerativeModel {
    val LLAMACPP_THREADS_DEFAULT = Runtime.getRuntime.availableProcessors()
    def create(
        path: Path,
        prompt: String,
        threads: Int = LLAMACPP_THREADS_DEFAULT
    ): Resource[IO, LlamacppGenerativeModel] =
      Resource.make(IO(createUnsafe(path, prompt, threads)))(_.close())

    def createUnsafe(path: Path, prompt: String, threads: Int = LLAMACPP_THREADS_DEFAULT) = {
      val params = new ModelParameters()
        .setModelFilePath(path.toString)
        .setNThreads(threads)
      val model = new LlamaModel(params)
      LlamacppGenerativeModel(model, prompt)
    }
  }
}

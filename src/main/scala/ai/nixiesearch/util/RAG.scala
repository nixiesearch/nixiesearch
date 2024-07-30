package ai.nixiesearch.util

import cats.effect.{ExitCode, IO, IOApp}
import de.kherud.llama.{InferenceParameters, LlamaModel, ModelParameters}
import fs2.Stream
import scala.jdk.CollectionConverters.*

object RAG {
  def makePrompt(system: String, user: String): String = {
    s"""<|start_header_id|>system<|end_header_id|>
       |
       |$system<|eot_id|><|start_header_id|>user<|end_header_id|>
       |
       |$user<|eot_id|><|start_header_id|>assistant<|end_header_id|>
       |
       |""".stripMargin
  }

//  override def run(args: List[String]): IO[ExitCode] =
  def main(args: Array[String]): Unit = {
    val params = new ModelParameters()
      .setModelFilePath(
        "/home/shutty/models/llama3-8b-instruct-gguf/Meta-Llama-3-8B-Instruct.Q4_0.gguf"
      )
      .setNThreads(16)
    val model     = new LlamaModel(params)
    val input     = makePrompt("", "knock knock")
    val inference = new InferenceParameters(input)
    val output    = model.generate(inference)
    println(output.iterator().asScala.map(_.text).toList)
    model.close()
  }

  case class RAGModel(handle: LlamaModel) {

    def generate(prompt: String): Stream[IO, String] = ???
    // Stream.eval(IO(handle.generate(new InferenceParameters(makePrompt("", prompt)))))
  }
}

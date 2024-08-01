package ai.nixiesearch.util

import ai.nixiesearch.core.nn.ModelHandle
import cats.effect.IO
import cats.effect.kernel.Resource
import de.kherud.llama.{InferenceParameters, LlamaModel}
import fs2.Stream

import scala.jdk.CollectionConverters.*

case class LLM(handle: LlamaModel, template: String, system: Option[String]) {
  def makePrompt(input: String): String = {
    system match {
      case Some(value) => template.replace("{system}", value).replace("{user}", input)
      case None        => template.replace("{user}", input)
    }

  }

  def generate(input: String): Stream[IO, String] = for {
    params   <- Stream.eval(IO(new InferenceParameters(makePrompt(input))))
    iterator <- Stream.eval(IO(handle.generate(params)))
    next <- Stream.unfoldEval(iterator.iterator().asScala)(it =>
      IO(it.hasNext).flatMap {
        case true  => IO(Some(it.next().text -> it))
        case false => IO(None)
      }
    )
  } yield {
    next
  }
}

object LLM {
  def create(handle: ModelHandle): Resource[IO, LLM] = ???
}

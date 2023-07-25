package ai.nixiesearch.core.nn.model.loader

import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.model.OnnxSession
import cats.effect.IO

trait ModelLoader[T <: ModelHandle] {
  def load(handle: T, dim: Int, modelFile: String, vocabFile: String): IO[OnnxSession]
}

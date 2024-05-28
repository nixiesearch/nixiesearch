package ai.nixiesearch.source

import ai.nixiesearch.core.Document
import cats.effect.IO
import fs2.Stream

trait DocumentSource {
  def stream(): Stream[IO, Document]
}

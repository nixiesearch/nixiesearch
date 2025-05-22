package ai.nixiesearch.main.subcommands.util

import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.Indexer
import ai.nixiesearch.index.sync.Index
import cats.effect.{IO, OutcomeIO, ResourceIO}
import fs2.Stream

object PeriodicFlushStream extends Logging {
  def run(index: Index, indexer: Indexer): ResourceIO[IO[OutcomeIO[Unit]]] = Stream
    .repeatEval(indexer.flush().flatMap {
      case false => IO.unit
      case true  => index.sync()
    })
    .metered(index.mapping.config.indexer.flush.interval)
    .compile
    .drain
    .background
}

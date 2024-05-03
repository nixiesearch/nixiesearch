package ai.nixiesearch.index.sync

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.index.store.StateClient
import cats.effect.IO

trait IndexSync {
  def sync(master: StateClient, replica: StateClient): IO[Unit]
}

object IndexSync {
  def create(mapping: IndexMapping): IO[IndexSync]
}

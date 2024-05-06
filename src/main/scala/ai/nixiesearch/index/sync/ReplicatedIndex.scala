package ai.nixiesearch.index.sync

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.store.StateClient
import cats.effect.IO
import org.apache.lucene.store.Directory

trait ReplicatedIndex extends Logging {
  def mapping: IndexMapping
  def encoders: BiEncoderCache
  def master: StateClient
  def replica: StateClient
  def directory: Directory
  def sync(): IO[Unit]
  def close(): IO[Unit]
}

object ReplicatedIndex {
  def create(mapping: IndexMapping): IO[ReplicatedIndex] = ???
}

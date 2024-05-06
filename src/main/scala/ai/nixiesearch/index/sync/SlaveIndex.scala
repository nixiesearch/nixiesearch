package ai.nixiesearch.index.sync

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.store.StateClient
import cats.effect.IO
import org.apache.lucene.store.Directory

case class SlaveIndex(
    mapping: IndexMapping,
    encoders: BiEncoderCache,
    master: StateClient,
    replica: StateClient,
    directory: Directory
) extends ReplicatedIndex {
  override def sync(): IO[Unit] = ???

  override def close(): IO[Unit] = ???
}

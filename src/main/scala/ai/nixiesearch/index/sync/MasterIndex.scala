package ai.nixiesearch.index.sync

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.store.StateClient
import cats.effect.{IO, Ref}
import org.apache.lucene.store.Directory

case class MasterIndex(
    mapping: IndexMapping,
    encoders: BiEncoderCache,
    master: StateClient,
    replica: StateClient,
    directory: Directory,
    seqnum: Ref[IO,Long]
) extends ReplicatedIndex
    with Logging {
  override def sync(): IO[Unit]   = ???
  override def close(): IO[Unit]  = IO.unit
  override def local: StateClient = master
}

package ai.nixiesearch.index.sync

import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.store.StateClient
import cats.effect.IO

case class MasterSlaveSync() extends IndexSync with Logging {
  override def sync(master: StateClient, replica: StateClient): IO[IndexSync.IndexSyncResult] = ???
}

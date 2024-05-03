package ai.nixiesearch.index.sync
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.store.StateClient
import ai.nixiesearch.index.sync.IndexSync.IndexSyncResult
import cats.effect.IO

case class NoopLocalSync() extends IndexSync with Logging {
  override def sync(master: StateClient, replica: StateClient): IO[IndexSyncResult] =
    debug("skipping index sync: standalone mode") *> IO.pure(IndexSyncResult(0L))
}

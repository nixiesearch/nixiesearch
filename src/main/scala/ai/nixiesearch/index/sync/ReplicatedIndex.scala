package ai.nixiesearch.index.sync

import ai.nixiesearch.config.{IndexCacheConfig, StoreConfig}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.embedding.BiEncoderCache
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.manifest.IndexManifest.ChangedFileOp
import ai.nixiesearch.index.store.StateClient
import cats.effect.{IO, Ref, Resource}
import org.apache.lucene.store.Directory
import fs2.Stream

trait ReplicatedIndex extends Index {
  override def sync(): IO[Boolean] = for {
    _                     <- debug("replicated index sync in progress")
    masterManifestOption  <- master.readManifest()
    replicaManifestOption <- replica.readManifest()
    changed <- (masterManifestOption, replicaManifestOption) match {
      case (Some(masterManifest), Some(replicaManifest)) if masterManifest.seqnum > replicaManifest.seqnum =>
        Stream
          .evalSeq(masterManifest.diff(Some(replicaManifest)))
          .evalMap {
            case ChangedFileOp.Add(fileName) => replica.write(fileName, master.read(fileName))
            case ChangedFileOp.Del(fileName) => replica.delete(fileName)
          }
          .compile
          .drain
          .map(_ => true)
      case (Some(masterManifest), Some(replicaManifest)) =>
        info(s"local seqnum=${masterManifest.seqnum} remote=${replicaManifest.seqnum}, no need to sync") *> IO(false)
      case (Some(masterManifest), None) =>
        Stream
          .evalSeq(masterManifest.diff(None))
          .evalMap {
            case ChangedFileOp.Add(fileName) => replica.write(fileName, master.read(fileName))
            case ChangedFileOp.Del(fileName) => replica.delete(fileName)
          }
          .compile
          .drain
          .map(_ => true)

      case (None, Some(replicaManifest)) =>
        IO.raiseError(new Exception(s"local manifest empty for index '${mapping.name}'"))
      case (None, None) =>
        IO.raiseError(new Exception(s"both manifests for local and remote are empty for index '${mapping.name}'"))
    }
    _ <- debug("index sync done")
  } yield {
    changed
  }

}

object ReplicatedIndex {}

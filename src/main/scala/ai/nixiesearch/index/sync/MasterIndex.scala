package ai.nixiesearch.index.sync

import ai.nixiesearch.config.CacheConfig
import ai.nixiesearch.config.StoreConfig.DistributedStoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.manifest.IndexManifest.ChangedFileOp
import ai.nixiesearch.index.store.{DirectoryStateClient, StateClient}
import cats.effect.{IO, Ref, Resource}
import org.apache.lucene.store.Directory

import scala.concurrent.duration.*
import fs2.Stream

case class MasterIndex(
    mapping: IndexMapping,
    encoders: BiEncoderCache,
    master: StateClient,
    replica: StateClient,
    directory: Directory,
    seqnum: Ref[IO, Long]
) extends ReplicatedIndex
    with Logging {
  override def sync(): IO[Unit] = for {
    masterManifestOption  <- master.readManifest()
    replicaManifestOption <- replica.readManifest()
    _ <- (masterManifestOption, replicaManifestOption) match {
      case (Some(masterManifest), Some(replicaManifest)) if masterManifest.seqnum > replicaManifest.seqnum =>
        Stream
          .evalSeq(IO(IndexManifest.diff(masterManifest, replicaManifest)))
          .evalMap {
            case ChangedFileOp.Add(fileName) => replica.write(fileName, master.read(fileName))
            case ChangedFileOp.Del(fileName) => replica.delete(fileName)
          }
          .compile
          .drain
      case (Some(masterManifest), Some(replicaManifest)) =>
        info(s"local seqnum=${masterManifest.seqnum} remote=${replicaManifest.seqnum}, no need to sync")
      case (Some(masterManifest), None) =>
        Stream
          .evalSeq(IO(IndexManifest.diff(masterManifest)))
          .evalMap {
            case ChangedFileOp.Add(fileName) => replica.write(fileName, master.read(fileName))
            case ChangedFileOp.Del(fileName) => replica.delete(fileName)
          }
          .compile
          .drain

      case (None, Some(replicaManifest)) =>
        IO.raiseError(new Exception(s"local manifest empty for index '${mapping.name}'"))
      case (None, None) =>
        IO.raiseError(new Exception(s"both manifests for local and remote are empty for index '${mapping.name}'"))
    }
  } yield {}
  override def close(): IO[Unit]  = IO.unit
  override def local: StateClient = master
}

object MasterIndex extends Logging {
  def create(configMapping: IndexMapping, conf: DistributedStoreConfig, cache: CacheConfig): Resource[IO, MasterIndex] =
    for {
      replicaState <- StateClient.createRemote(conf.remote, configMapping.name)
      directory    <- LocalDirectory.fromRemote(conf.indexer, replicaState, configMapping.name)
      masterState  <- Resource.pure(DirectoryStateClient(directory, configMapping.name))

      manifest <- Resource.eval(LocalIndex.readOrCreateManifest(masterState, configMapping))
      handles  <- Resource.pure(manifest.mapping.modelHandles())
      encoders <- BiEncoderCache.create(handles, cache.embedding)
      _        <- Resource.eval(info(s"index ${manifest.mapping.name} opened"))
      seqnum   <- Resource.eval(Ref.of[IO, Long](manifest.seqnum))
      index <- Resource.pure(
        MasterIndex(
          mapping = manifest.mapping,
          encoders = encoders,
          master = masterState,
          replica = replicaState,
          directory = directory,
          seqnum = seqnum
        )
      )
      _ <- fs2.Stream.repeatEval(index.sync()).metered(1.second).compile.drain.background
    } yield {
      index
    }

}

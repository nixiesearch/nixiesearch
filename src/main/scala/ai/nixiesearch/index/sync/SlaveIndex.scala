package ai.nixiesearch.index.sync

import ai.nixiesearch.config.{CacheConfig, IndexCacheConfig}
import ai.nixiesearch.config.StoreConfig.DistributedStoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.index.Models
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.manifest.IndexManifest.ChangedFileOp
import ai.nixiesearch.index.store.{DirectoryStateClient, StateClient}
import cats.effect.{IO, Ref, Resource}
import fs2.Stream
import org.apache.lucene.store.Directory

import scala.concurrent.duration.*

case class SlaveIndex(
    mapping: IndexMapping,
    models: Models,
    master: StateClient,
    replica: StateClient,
    directory: Directory,
    seqnum: Ref[IO, Long]
) extends ReplicatedIndex {
  override def sync(): IO[Boolean] = for {
    _                     <- debug("index sync remote->slave in progress")
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
  } yield { changed }

  override def local: StateClient = replica
}

object SlaveIndex extends Logging {
  def create(
      configMapping: IndexMapping,
      conf: DistributedStoreConfig,
      cacheConfig: CacheConfig
  ): Resource[IO, SlaveIndex] =
    for {
      _              <- Resource.eval(debug(s"creating SlaveIndex for index=${configMapping.name} conf=$conf"))
      masterState    <- StateClient.createRemote(conf.remote, configMapping.name)
      directory      <- LocalDirectory.fromRemote(conf.indexer, masterState, configMapping.name)
      replicaState   <- DirectoryStateClient.create(directory, configMapping.name)
      manifestOption <- Resource.eval(replicaState.readManifest())
      manifest <- Resource.eval(IO.fromOption(manifestOption)(BackendError("index.json file not found in the index")))
      handles  <- Resource.pure(manifest.mapping.modelHandles())
      models   <- Models.create(handles, Nil, cacheConfig)
      seqnum   <- Resource.eval(Ref.of[IO, Long](manifest.seqnum))
      index <- Resource.make(
        IO(
          SlaveIndex(
            mapping = manifest.mapping,
            models = models,
            master = masterState,
            replica = replicaState,
            directory = directory,
            seqnum = seqnum
          )
        )
      )(index => debug(s"closing slave index=${index.mapping.name}"))
      _ <- Resource.eval(index.sync())
      _ <- Resource.eval(info(s"slave index ${manifest.mapping.name} opened for search"))
    } yield {
      index
    }
}

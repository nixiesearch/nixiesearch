package ai.nixiesearch.index.sync

import ai.nixiesearch.config.{CacheConfig, IndexCacheConfig}
import ai.nixiesearch.config.StoreConfig.DistributedStoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.index.Models
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.manifest.IndexManifest.ChangedFileOp
import ai.nixiesearch.index.store.{DirectoryStateClient, StateClient}
import cats.effect.{IO, Ref, Resource}
import org.apache.lucene.store.Directory

import scala.concurrent.duration.*
import fs2.Stream

case class MasterIndex(
    mapping: IndexMapping,
    models: Models,
    master: StateClient,
    replica: StateClient,
    directory: Directory,
    seqnum: Ref[IO, Long]
) extends ReplicatedIndex
    with Logging {
  override def local: StateClient = master
}

object MasterIndex extends Logging {
  def create(
      configMapping: IndexMapping,
      conf: DistributedStoreConfig,
      cacheConfig: CacheConfig
  ): Resource[IO, MasterIndex] =
    for {
      replicaState <- StateClient.createRemote(conf.remote, configMapping.name)
      directory    <- LocalDirectory.fromRemote(conf.indexer, replicaState, configMapping.name)
      masterState  <- DirectoryStateClient.create(directory, configMapping.name)

      manifest <- Resource.eval(LocalIndex.readOrCreateManifest(masterState, configMapping))
      handles  <- Resource.pure(manifest.mapping.modelHandles())
      models   <- Models.create(handles, Nil, cacheConfig)
      seqnum   <- Resource.eval(Ref.of[IO, Long](manifest.seqnum))
      index <- Resource.make(
        IO(
          MasterIndex(
            mapping = manifest.mapping,
            models = models,
            master = masterState,
            replica = replicaState,
            directory = directory,
            seqnum = seqnum
          )
        )
      )(index => debug(s"closing master index=${index.mapping.name}"))
      _ <- Resource.eval(info(s"Master index ${manifest.mapping.name} opened for writing"))
    } yield {
      index
    }

}

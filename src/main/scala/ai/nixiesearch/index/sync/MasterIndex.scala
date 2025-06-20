package ai.nixiesearch.index.sync

import ai.nixiesearch.config.{CacheConfig, InferenceConfig}
import ai.nixiesearch.config.StoreConfig.DistributedStoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.Models
import ai.nixiesearch.index.store.{DirectoryStateClient, StateClient}
import cats.effect.{IO, Ref, Resource}
import org.apache.lucene.store.Directory

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
      models: Models
  ): Resource[IO, MasterIndex] =
    for {
      replicaState <- StateClient.createRemote(conf.remote, configMapping.name)
      directory    <- LocalDirectory.fromRemote(conf.indexer, replicaState, configMapping.name)
      masterState  <- DirectoryStateClient.create(directory, configMapping.name)
      manifest     <- Resource.eval(LocalIndex.readOrCreateManifest(masterState, configMapping))
      seqnum       <- Resource.eval(Ref.of[IO, Long](manifest.seqnum))
      index        <- Resource.make(
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

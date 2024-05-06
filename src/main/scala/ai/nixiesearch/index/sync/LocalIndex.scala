package ai.nixiesearch.index.sync
import ai.nixiesearch.config.{CacheConfig, StoreConfig}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.store.{DirectoryStateClient, StateClient}
import cats.effect.{IO, Resource}
import org.apache.lucene.store.Directory

case class LocalIndex(
    mapping: IndexMapping,
    encoders: BiEncoderCache,
    master: StateClient,
    replica: StateClient,
    directory: Directory
) extends ReplicatedIndex {
  override def sync(): IO[Unit] = debug("skipping index sync: standalone mode")

  override def close(): IO[Unit] = IO.unit
}

object LocalIndex extends Logging {
  def create(
      mapping: IndexMapping,
      config: StoreConfig.LocalStoreConfig,
      cache: CacheConfig
  ): Resource[IO, LocalIndex] = {
    val make = for {
      directory <- LocalDirectory.create(config, mapping.name)
      state     <- IO(DirectoryStateClient(directory, mapping))
      handles   <- IO(mapping.modelHandles())
      encoders  <- BiEncoderCache.create(handles, cache.embedding)
      _         <- info(s"index ${mapping.name} opened")
    } yield {
      LocalIndex(
        mapping = mapping,
        encoders = encoders,
        master = state,
        replica = state,
        directory = directory
      )
    }
    Resource.make(make)(index => IO(index.directory.close()))
  }
}

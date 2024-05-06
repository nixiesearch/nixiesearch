package ai.nixiesearch.index.sync
import ai.nixiesearch.config.{CacheConfig, StoreConfig}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.store.StateClient.StateError
import ai.nixiesearch.index.store.{DirectoryStateClient, StateClient}
import cats.effect.{IO, Ref, Resource}
import fs2.concurrent.SignallingRef
import org.apache.lucene.store.Directory
import fs2.{Chunk, Stream}
import io.circe.syntax.*

import scala.concurrent.duration.*
import java.nio.ByteBuffer

case class LocalIndex(
    mapping: IndexMapping,
    encoders: BiEncoderCache,
    master: StateClient,
    directory: Directory,
    seqnum: Ref[IO, Long]
) extends ReplicatedIndex {
  override def sync(): IO[Unit] = for {
    manifest <- master.readManifest().flatMap {
      case None =>
        IO.raiseError(StateError.InconsistentStateError("missing manifest for opened index: this should never happen!"))
      case Some(m) => IO.pure(m)
    }
    _ <- seqnum.set(manifest.seqnum)
    _ <- debug(s"local index '${mapping.name}' sync done, seqnum=${manifest.seqnum}")
  } yield {}

  override def close(): IO[Unit] = IO.unit

  override def local: StateClient   = master
  override def replica: StateClient = master
}

object LocalIndex extends Logging {
  def create(
      configMapping: IndexMapping,
      config: StoreConfig.LocalStoreConfig,
      cache: CacheConfig
  ): Resource[IO, LocalIndex] = {
    for {
      directory <- LocalDirectory.fromLocal(config.local, configMapping.name)
      state     <- Resource.pure(DirectoryStateClient(directory, configMapping.name))
      manifest  <- Resource.eval(readOrCreateManifest(state, configMapping))
      handles   <- Resource.pure(manifest.mapping.modelHandles())
      encoders  <- BiEncoderCache.create(handles, cache.embedding)
      _         <- Resource.eval(info(s"index ${manifest.mapping.name} opened"))
      seqnum    <- Resource.eval(Ref.of[IO, Long](manifest.seqnum))
      index <- Resource.pure(
        LocalIndex(
          mapping = manifest.mapping,
          encoders = encoders,
          master = state,
          directory = directory,
          seqnum = seqnum
        )
      )
      _ <- Stream.repeatEval(index.sync()).metered(1.second).compile.drain.background
    } yield {
      index
    }
  }

  def readOrCreateManifest(state: StateClient, configMapping: IndexMapping): IO[IndexManifest] = {
    state.readManifest().flatMap {
      case None =>
        for {
          _        <- info("index dir does not contain manifest, creating...")
          manifest <- state.createManifest(configMapping, 0L)
          _ <- state.write(
            IndexManifest.MANIFEST_FILE_NAME,
            Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(manifest.asJson.spaces2.getBytes())))
          )
        } yield {
          manifest
        }
      case Some(indexManifest) =>
        for {
          mergedMapping <- indexManifest.mapping.migrate(configMapping)
          manifest      <- state.createManifest(mergedMapping, indexManifest.seqnum)
          _ <- state.write(
            IndexManifest.MANIFEST_FILE_NAME,
            Stream.chunk(Chunk.byteBuffer(ByteBuffer.wrap(manifest.asJson.spaces2.getBytes())))
          )
        } yield {
          manifest
        }
    }
  }
}

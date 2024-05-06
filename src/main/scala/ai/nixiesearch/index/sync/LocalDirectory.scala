package ai.nixiesearch.index.sync

import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.StoreConfig.{LocalStoreConfig, LocalStoreLocation}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.manifest.IndexManifest.ChangedFileOp
import ai.nixiesearch.index.store.{DirectoryStateClient, StateClient}
import cats.effect.{IO, Resource}
import org.apache.lucene.store.{ByteBuffersDirectory, Directory, MMapDirectory}
import fs2.Stream

import java.nio.file.{Files, Path}

object LocalDirectory extends Logging {
  case class DirectoryError(m: String) extends Exception(m)
  def fromLocal(local: LocalStoreLocation, indexName: String): Resource[IO, Directory] = local match {
    case StoreConfig.LocalStoreLocation.DiskLocation(path) =>
      for {
        _             <- Resource.eval(info("initialized MMapDirectory"))
        safeIndexPath <- Resource.eval(indexPath(path, indexName))
        directory     <- Resource.make(IO(new MMapDirectory(safeIndexPath)))(dir => IO(dir.close()))
      } yield {
        directory
      }
    case StoreConfig.LocalStoreLocation.MemoryLocation() =>
      for {
        _         <- Resource.eval(info("initialized in-mem ByteBuffersDirectory"))
        directory <- Resource.make(IO(new ByteBuffersDirectory()))(dir => IO(dir.close()))
      } yield {
        directory
      }
  }

  def fromRemote(
      localLocation: LocalStoreLocation,
      remote: StateClient,
      indexName: String
  ): Resource[IO, Directory] = for {
    directory            <- fromLocal(localLocation, indexName)
    local                <- Resource.pure(DirectoryStateClient(directory, indexName))
    localManifestOption  <- Resource.eval(local.readManifest())
    remoteManifestOption <- Resource.eval(remote.readManifest())
    _ <- (localManifestOption, remoteManifestOption) match {
      case (None, None) => Resource.eval(info("both local and remote manifests are missing, empty index created?"))
      case (Some(localManifest), None) =>
        Resource.eval(
          info("remote location is empty, doing full sync local -> remote") *> Stream
            .emits(localManifest.files)
            .evalMap(file => remote.write(file.name, local.read(file.name)))
            .compile
            .drain *> info("local -> remote full sync done")
        )
      case (None, Some(remoteManifest)) =>
        Resource.eval(
          info("local index is empty, doing full sync remote -> local") *> Stream
            .emits(remoteManifest.files)
            .evalMap(file => local.write(file.name, remote.read(file.name)))
            .compile
            .drain *> info("remote -> local full sync done")
        )
      case (Some(localManifest), Some(remoteManifest)) =>
        Resource.eval(for {
          _ <- info(s"local=${localManifest.seqnum} remote=${remoteManifest.seqnum}")
          _ <- (localManifest.seqnum, remoteManifest.seqnum) match {
            case (ls, rs) if ls < rs =>
              Stream
                .evalSeq(IO(IndexManifest.diff(remoteManifest, localManifest)))
                .evalMap {
                  case ChangedFileOp.Add(fileName) => local.write(fileName, remote.read(fileName))
                  case ChangedFileOp.Del(fileName) => local.delete(fileName)
                }
                .compile
                .drain
            case (ls, rs) if ls == rs =>
              info(s"both local and remote for index '$indexName' are the same, skipping sync")
            case (ls, rs) if ls > rs =>
              Stream
                .evalSeq(IO(IndexManifest.diff(localManifest, remoteManifest)))
                .evalMap {
                  case ChangedFileOp.Add(fileName) => remote.write(fileName, local.read(fileName))
                  case ChangedFileOp.Del(fileName) => remote.delete(fileName)
                }
                .compile
                .drain
          }
        } yield {})
    }
  } yield {
    directory
  }

  def indexPath(path: Path, indexName: String): IO[Path] = for {
    indexPath <- IO(path.resolve(indexName))
    exists    <- IO(Files.exists(indexPath))
    _ <- IO.whenA(!exists)(IO(Files.createDirectories(indexPath)) *> info(s"created on-disk directory $indexPath"))
    _ <- IO.whenA(exists)(IO(Files.isDirectory(indexPath)).flatMap {
      case true  => IO.unit
      case false => IO.raiseError(DirectoryError(s"on-disk directory $indexPath exists, but it's a file"))
    })
  } yield {
    indexPath
  }
}

package ai.nixiesearch.index.sync

import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation
import ai.nixiesearch.config.mapping.IndexConfig.DirectoryType
import ai.nixiesearch.config.mapping.IndexName
import ai.nixiesearch.core.Logging
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.manifest.IndexManifest.ChangedFileOp
import ai.nixiesearch.index.store.{DirectoryStateClient, StateClient}
import ai.nixiesearch.util.Version
import cats.effect.{IO, Resource}
import org.apache.lucene.store.{ByteBuffersDirectory, Directory, MMapDirectory, NIOFSDirectory}
import fs2.Stream

import java.nio.file.{Files, Path}

object LocalDirectory extends Logging {
  case class DirectoryError(m: String) extends Exception(m)

  private def makeDirectory(directoryType: DirectoryType, path: Path): Directory = directoryType match {
    case DirectoryType.MMapDirectoryType if Version.isGraalVMNativeImage =>
      logger.warn(
        "Configured to use MMapDirectory, but we're running in a native image: falling back to NIOFSDirectory"
      )
      new NIOFSDirectory(path)
    case DirectoryType.MMapDirectoryType =>
      logger.info("Using MMapDiretory")
      new MMapDirectory(path)
    case DirectoryType.NIOFSDirectoryType =>
      logger.info("Using NIOFSDiretory")
      new NIOFSDirectory(path)
  }
  def fromLocal(
      local: LocalStoreLocation,
      indexName: IndexName,
      directoryType: DirectoryType
  ): Resource[IO, Directory] = local match {
    case StoreConfig.LocalStoreLocation.DiskLocation(path) =>
      for {
        safeIndexPath <- Resource.eval(indexPath(path, indexName))
        directory     <- Resource.make(IO(makeDirectory(directoryType, safeIndexPath)))(dir => IO(dir.close()))

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
      indexName: IndexName,
      directoryType: DirectoryType
  ): Resource[IO, Directory] = for {
    directory            <- fromLocal(localLocation, indexName, directoryType)
    local                <- Resource.pure(DirectoryStateClient(directory, indexName))
    localManifestOption  <- Resource.eval(local.readManifest())
    remoteManifestOption <- Resource.eval(remote.readManifest())
    _                    <- (localManifestOption, remoteManifestOption) match {
      case (None, None) => Resource.eval(info("both local and remote manifests are missing, empty index created?"))
      case (Some(localManifest), None) =>
        Resource.eval(
          info("remote location is empty, doing full sync local -> remote") *> Stream
            .evalSeq(localManifest.diff(None))
            .evalMap {
              case ChangedFileOp.Add(fileName, size) => remote.write(fileName, local.read(fileName, size))
              case ChangedFileOp.Del(fileName)       => IO.unit
            }
            .compile
            .drain *> info("local -> remote full sync done")
        )
      case (None, Some(remoteManifest)) =>
        Resource.eval(
          info("local index is empty, doing full sync remote -> local") *> Stream
            .evalSeq(remoteManifest.diff(None))
            .evalMap {
              case ChangedFileOp.Add(fileName, size) => local.write(fileName, remote.read(fileName, size))
              case ChangedFileOp.Del(fileName)       => IO.unit
            }
            .compile
            .drain *> info("remote -> local full sync done")
        )
      case (Some(localManifest), Some(remoteManifest)) =>
        Resource.eval(for {
          _ <- info(s"local=${localManifest.seqnum} remote=${remoteManifest.seqnum}")
          _ <- (localManifest.seqnum, remoteManifest.seqnum) match {
            case (ls, rs) if ls < rs =>
              Stream
                .evalSeq(remoteManifest.diff(Some(localManifest)))
                .evalMap {
                  case ChangedFileOp.Add(fileName, size) => local.write(fileName, remote.read(fileName, size))
                  case ChangedFileOp.Del(fileName)       => local.delete(fileName)
                }
                .compile
                .drain
            case (ls, rs) if ls > rs =>
              Stream
                .evalSeq(localManifest.diff(Some(remoteManifest)))
                .evalMap {
                  case ChangedFileOp.Add(fileName, size) => remote.write(fileName, local.read(fileName, size))
                  case ChangedFileOp.Del(fileName)       => remote.delete(fileName)
                }
                .compile
                .drain
            case (ls, rs) =>
              info(s"both local and remote for index '$indexName' are the same, skipping sync")
          }
        } yield {})
    }
  } yield {
    directory
  }

  def indexPath(path: Path, indexName: IndexName): IO[Path] = for {
    indexPath <- IO(path.resolve(indexName.value))
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

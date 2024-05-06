package ai.nixiesearch.index.sync

import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.core.Logging
import cats.effect.{IO, Resource}
import org.apache.lucene.store.{ByteBuffersDirectory, Directory, MMapDirectory}

import java.nio.file.{Files, Path}

object LocalDirectory extends Logging {
  case class DirectoryError(m: String) extends Exception(m)
  def create(config: LocalStoreConfig, indexName: String): Resource[IO, Directory] = config.local match {
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

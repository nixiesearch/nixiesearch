package ai.nixiesearch.index.sync

import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.core.Logging
import cats.effect.IO
import org.apache.lucene.store.{ByteBuffersDirectory, Directory, MMapDirectory}

import java.nio.file.{Files, Path}

object LocalDirectory extends Logging {
  case class DirectoryError(m: String) extends Exception(m)
  def create(config: LocalStoreConfig, indexName: String): IO[Directory] = config.local match {
    case StoreConfig.LocalStoreLocation.DiskLocation(path) =>
      for {
        _             <- info("initialized MMapDirectory")
        safeIndexPath <- indexPath(path, indexName)
        directory     <- IO(new MMapDirectory(safeIndexPath))
      } yield {
        directory
      }
    case StoreConfig.LocalStoreLocation.MemoryLocation() =>
      info("initialized ByteBuffersDirectory") *> IO(new ByteBuffersDirectory())
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

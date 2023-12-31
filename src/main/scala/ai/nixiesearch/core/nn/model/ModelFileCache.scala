package ai.nixiesearch.core.nn.model

import ai.nixiesearch.core.Logging
import cats.effect.IO
import org.apache.commons.lang3.SystemUtils
import fs2.io.file.Files
import fs2.io.readInputStream
import org.apache.commons.io.IOUtils

import java.io.{ByteArrayInputStream, File, FileInputStream}
import java.nio.file.Path

case class ModelFileCache(cacheDir: String) extends Logging {
  def getIfExists(dir: String, file: String): IO[Option[Array[Byte]]] = for {
    targetFile <- IO(new File(cacheDir + File.separator + dir + File.separator + file))
    contents <- targetFile.exists() match {
      case true  => IO(Some(IOUtils.toByteArray(new FileInputStream(targetFile))))
      case false => IO.none
    }
  } yield {
    contents
  }

  def put(dir: String, file: String, bytes: Array[Byte]): IO[Unit] = for {
    targetDir  <- IO(new File(cacheDir + File.separator + dir))
    targetFile <- IO(new File(cacheDir + File.separator + dir + File.separator + file))
    _          <- IO.whenA(!targetDir.exists())(IO(targetDir.mkdirs()))
    _          <- info(s"writing cache file $dir/$file")
    _ <- readInputStream[IO](IO(new ByteArrayInputStream(bytes)), 1024)
      .through(Files[IO].writeAll(fs2.io.file.Path(targetFile.toString)))
      .compile
      .drain
  } yield {}
}

object ModelFileCache extends Logging {
  def create() = {
    for {
      topDir   <- cacheDir()
      nixieDir <- IO(new File(topDir.toString + File.separator + "nixiesearch"))
      _ <- IO.whenA(!nixieDir.exists())(
        info(s"cache dir $nixieDir is not present, creating") *> IO(nixieDir.mkdirs())
      )
      _ <- info(s"using $nixieDir as local cache dir")
    } yield {
      ModelFileCache(nixieDir.toString)
    }
  }

  def cacheDir() = IO {
    val fallback = Path.of(System.getProperty("java.io.tmpdir"))
    val dir = if (SystemUtils.IS_OS_WINDOWS) {
      Option(System.getenv("LOCALAPPDATA")).map(path => Path.of(path)).filter(_.toFile.exists()).getOrElse(fallback)
    } else if (SystemUtils.IS_OS_MAC) {
      Option(System.getProperty("user.home"))
        .map(home => s"$home/Library/Caches")
        .map(path => Path.of(path))
        .filter(_.toFile.exists())
        .getOrElse(fallback)
    } else if (SystemUtils.IS_OS_LINUX) {
      val default = Option(System.getProperty("user.home"))
        .map(home => s"$home/.cache")
        .map(path => Path.of(path))
        .filter(_.toFile.exists())
      Option(System.getenv("XDG_CACHE_HOME"))
        .map(path => Path.of(path))
        .filter(_.toFile.exists())
        .orElse(default)
        .getOrElse(fallback)
    } else {
      fallback
    }
    dir
  }
}

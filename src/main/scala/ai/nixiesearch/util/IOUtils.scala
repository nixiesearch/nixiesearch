package ai.nixiesearch.util

import cats.effect.IO

import java.nio.file.Path
import fs2.io.readInputStream

import java.io.FileInputStream

object IOUtils {
  def readFileToString(path: Path): IO[String] = readInputStream[IO](IO(new FileInputStream(path.toFile)), 1024)
    .through(fs2.text.utf8.decode)
    .compile
    .fold(new StringBuilder())((builder, next) => builder.append(next))
    .map(_.toString())
}

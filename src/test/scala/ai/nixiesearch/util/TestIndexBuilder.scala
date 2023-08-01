package ai.nixiesearch.util

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.index.IndexBuilder
import cats.effect.unsafe.implicits.global
import java.nio.file.{Files, Paths}

object TestIndexBuilder {
  def apply(mapping: IndexMapping = TestIndexMapping()): IndexBuilder = {
    val dir = Files.createTempDirectory("index")
    IndexBuilder.create(dir, mapping).unsafeRunSync()
  }
}

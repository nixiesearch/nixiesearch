package ai.nixiesearch.util

import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.index.IndexBuilder

import java.nio.file.{Files, Paths}

object TestIndexBuilder {
  def apply(mapping: IndexMapping = TestIndexMapping()): IndexBuilder = {
    val dir = Files.createTempDirectory("index")
    IndexBuilder.open(dir, mapping)
  }
}

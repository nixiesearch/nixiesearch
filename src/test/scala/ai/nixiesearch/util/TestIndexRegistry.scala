package ai.nixiesearch.util

import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.StoreUrl.LocalStoreUrl
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.index.IndexRegistry
import cats.effect.unsafe.implicits.global
import java.nio.file.Files

object TestIndexRegistry {
  def apply(indices: List[IndexMapping] = Nil): IndexRegistry = {
    val dir = Files.createTempDirectory("nixie-tmp")
    dir.toFile.deleteOnExit()
    val registry =
      IndexRegistry
        .create(LocalStoreConfig(LocalStoreUrl(dir.toString)), indices)
        .allocated
        .unsafeRunSync()
        ._1
    registry
  }
}

package ai.nixiesearch.util

import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.StoreUrl.LocalStoreUrl
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.LocalIndex
import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global

import java.nio.file.{Files, Path}

object TestLocalIndex {
  def apply(index: IndexMapping = TestIndexMapping()): LocalIndex = {
    val dir = Files.createTempDirectory("nixie")
    dir.toFile.deleteOnExit()
    val mappingRef = Ref.of[IO, Option[IndexMapping]](Some(index)).unsafeRunSync()
    val encoders   = BiEncoderCache.create().allocated.unsafeRunSync()._1
    LocalIndex(LocalStoreConfig(LocalStoreUrl(dir.toString)), mappingRef, encoders)
  }
}

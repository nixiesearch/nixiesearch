package ai.nixiesearch.index.sync

import ai.nixiesearch.config.StoreConfig.LocalStoreLocation
import ai.nixiesearch.config.StoreConfig
import org.scalatest.flatspec.AnyFlatSpec

class LocalIndexMemTest extends LocalIndexSuite {
  override val config = StoreConfig.LocalStoreConfig(LocalStoreLocation.MemoryLocation())
}

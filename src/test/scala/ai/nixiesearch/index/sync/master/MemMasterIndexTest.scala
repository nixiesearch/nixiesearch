package ai.nixiesearch.index.sync.master
import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.StoreConfig.BlockStoreLocation.RemoteDiskLocation
import ai.nixiesearch.config.StoreConfig.DistributedStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation

import java.nio.file.Files

class MemMasterIndexTest extends MasterIndexSuite {
  override def config: StoreConfig.DistributedStoreConfig = DistributedStoreConfig(
    searcher = MemoryLocation(),
    indexer = MemoryLocation(),
    remote = RemoteDiskLocation(Files.createTempDirectory("nixie_tmp_"))
  )
}

package ai.nixiesearch.index.sync
import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.DiskLocation

import java.nio.file.{Files, Path}

class LocalIndexDiskTest extends LocalIndexSuite {
  override def config: StoreConfig.LocalStoreConfig =
    StoreConfig.LocalStoreConfig(DiskLocation(Files.createTempDirectory("nixie_tmp_")))

}

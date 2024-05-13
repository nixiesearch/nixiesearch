package ai.nixiesearch.index.store

import ai.nixiesearch.util.TestIndexMapping

import java.nio.file.Files

class RemotePathStateClientTest extends StateClientSuite[RemotePathStateClient] {
  override def client() =
    RemotePathStateClient.create(Files.createTempDirectory("nixiesearch_tmp_"), "test")
}

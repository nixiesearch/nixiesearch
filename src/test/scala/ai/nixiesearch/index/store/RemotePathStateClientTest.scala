package ai.nixiesearch.index.store

import java.nio.file.Files

class RemotePathStateClientTest extends StateClientSuite[RemotePathStateClient] {
  override def client(): RemotePathStateClient = RemotePathStateClient(Files.createTempDirectory("nixiesearch_tmp_"))
}

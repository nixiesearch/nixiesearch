package ai.nixiesearch.index.store

import org.apache.lucene.store.ByteBuffersDirectory

class DirectoryStateClientTest extends StateClientSuite[DirectoryStateClient] {
  override def client(): DirectoryStateClient = DirectoryStateClient(new ByteBuffersDirectory())
}

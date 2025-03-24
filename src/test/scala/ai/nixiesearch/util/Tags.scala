package ai.nixiesearch.util

import org.scalatest.Tag

object Tags {
  object EndToEnd {
    object Index         extends Tag("e2e-index")
    object Embeddings    extends Tag("e2e-embed")
    object APIEmbeddings extends Tag("e2e-api-embed")
    object Network       extends Tag("e2e-network")
  }
}

package ai.nixiesearch.util

import org.scalatest.Tag

object Tags {
  object EndToEnd {
    object Index      extends Tag("e2e-index")
    object Embeddings extends Tag("e2e-embed")
    object Network    extends Tag("e2e-network")
  }
}

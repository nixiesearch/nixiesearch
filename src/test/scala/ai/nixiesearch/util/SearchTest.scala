package ai.nixiesearch.util

import ai.nixiesearch.config.InferenceConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Document
import org.scalatest.flatspec.AnyFlatSpec
import cats.effect.unsafe.implicits.global

trait SearchTest extends AnyFlatSpec {
  def inference: InferenceConfig = TestInferenceConfig()
  def mapping: IndexMapping
  def docs: List[Document]

  def withIndex(code: LocalNixie => Any): Unit = {
    val (cluster, shutdown) = LocalNixie.create(mapping, inference).allocated.unsafeRunSync()
    try {
      if (docs.nonEmpty) {
        cluster.indexer.addDocuments(docs).unsafeRunSync()
        cluster.indexer.flush().unsafeRunSync()
        cluster.indexer.index.sync().unsafeRunSync()
        cluster.searcher.sync().unsafeRunSync()
      }
      code(cluster)
    } finally {
      shutdown.unsafeRunSync()
    }
  }

}

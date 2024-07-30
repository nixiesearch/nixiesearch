package ai.nixiesearch.util

import ai.nixiesearch.api.SearchRoute
import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.{MatchAllQuery, Query}
import ai.nixiesearch.config.IndexCacheConfig.EmbeddingCacheConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import cats.effect.unsafe.implicits.global
import org.apache.commons.io.FileUtils
import org.apache.lucene.search.MatchAllDocsQuery
import org.scalatest.BeforeAndAfterAll

import java.io.File
import java.nio.file.{Files, Path}
import scala.collection.mutable.ArrayBuffer

trait SearchTest extends AnyFlatSpec {
  def mapping: IndexMapping
  def docs: List[Document]

  def withIndex(code: LocalNixie => Any): Unit = {
    val (cluster, shutdown) = LocalNixie.create(mapping).allocated.unsafeRunSync()
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

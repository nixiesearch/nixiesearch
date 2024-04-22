package ai.nixiesearch.util

import ai.nixiesearch.api.SearchRoute
import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.{MatchAllQuery, Query}
import ai.nixiesearch.config.CacheConfig.EmbeddingCacheConfig
import ai.nixiesearch.config.StoreConfig.{LocalStoreConfig, MemoryStoreConfig}
import ai.nixiesearch.config.StoreConfig.StoreUrl.LocalStoreUrl
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.index.cluster.Searcher
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

  trait Index {
    lazy val cluster = {
      val c = LocalNixie.create(mapping).unsafeRunSync()
      c.indexer.index(mapping.name, docs).unsafeRunSync()
      c.indexer.flush(mapping.name).unsafeRunSync()
      c
    }

    def search(
        query: Query = MatchAllQuery(),
        filters: Filters = Filters(),
        aggs: Aggs = Aggs(),
        fields: List[String] = List("_id"),
        n: Int = 10
    ): List[String] = {
      cluster.searcher
        .search(mapping.name, SearchRequest(query, filters, n, fields, aggs))
        .unsafeRunSync()
        .hits
        .flatMap(_.fields.collect { case TextField(_, value) => value })
    }

    def searchRaw(
        query: Query = MatchAllQuery(),
        filters: Filters = Filters(),
        aggs: Aggs = Aggs(),
        fields: List[String] = List("_id"),
        n: Int = 10
    ): SearchRoute.SearchResponse = {
      cluster.searcher
        .search(mapping.name, SearchRequest(query, filters, n, fields, aggs))
        .unsafeRunSync()
    }
  }

}

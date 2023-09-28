package ai.nixiesearch.util

import ai.nixiesearch.api.SearchRoute
import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.{MatchAllQuery, Query}
import ai.nixiesearch.config.StoreConfig.{LocalStoreConfig, MemoryStoreConfig}
import ai.nixiesearch.config.StoreConfig.StoreUrl.LocalStoreUrl
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Document
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.core.search.Searcher
import ai.nixiesearch.index.IndexRegistry
import ai.nixiesearch.index.local.LocalIndex
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import cats.effect.unsafe.implicits.global
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll

import java.io.File
import java.nio.file.{Files, Path}
import scala.collection.mutable.ArrayBuffer

trait SearchTest extends AnyFlatSpec {
  def mapping: IndexMapping
  def docs: List[Document]

  trait Index {
    val registry = IndexRegistry.create(MemoryStoreConfig(), List(mapping)).allocated.unsafeRunSync()._1

    val index = {
      val w = registry.index(mapping.name).unsafeRunSync().get
      w.addDocuments(docs).unsafeRunSync()
      w.flush().unsafeRunSync()
      w
    }

    def search(
        query: Query = MatchAllQuery(),
        filters: Filters = Filters(),
        aggs: Aggs = Aggs(),
        fields: List[String] = List("_id"),
        n: Int = 10
    ): List[String] = {
      Searcher
        .search(SearchRequest(query, filters, n, fields, aggs), index)
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
      Searcher
        .search(SearchRequest(query, filters, n, fields, aggs), index)
        .unsafeRunSync()

    }
  }

}

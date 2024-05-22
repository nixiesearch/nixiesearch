package ai.nixiesearch.util

import ai.nixiesearch.api.SearchRoute
import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.{MatchAllQuery, Query}
import ai.nixiesearch.config.CacheConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.index.sync.LocalIndex
import ai.nixiesearch.index.{Searcher, Indexer}
import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global

case class LocalNixie(searcher: Searcher, indexer: Indexer) {
  def search(
      query: Query = MatchAllQuery(),
      filters: Filters = Filters(),
      aggs: Aggs = Aggs(),
      fields: List[String] = List("_id"),
      n: Int = 10
  ): List[String] = {
    searcher
      .search(SearchRequest(query, filters, n, fields, aggs))
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
    searcher
      .search(SearchRequest(query, filters, n, fields, aggs))
      .unsafeRunSync()
  }

}

object LocalNixie {
  def create(mapping: IndexMapping): Resource[IO, LocalNixie] = for {
    index    <- LocalIndex.create(mapping, LocalStoreConfig(MemoryLocation()))
    indexer  <- Indexer.open(index)
    searcher <- Searcher.open(index)
  } yield {
    LocalNixie(searcher, indexer)
  }
}

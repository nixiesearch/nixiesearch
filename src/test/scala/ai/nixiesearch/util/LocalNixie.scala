package ai.nixiesearch.util

import ai.nixiesearch.api.SearchRoute
import ai.nixiesearch.api.SearchRoute.{SearchRequest, SortPredicate}
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.api.query.retrieve.MatchAllQuery
import ai.nixiesearch.config.{CacheConfig, InferenceConfig}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.{FieldName, IndexMapping}
import ai.nixiesearch.core.Field.TextField
import ai.nixiesearch.core.metrics.Metrics
import ai.nixiesearch.index.sync.LocalIndex
import ai.nixiesearch.index.{Indexer, Models, Searcher}
import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global

case class LocalNixie(searcher: Searcher, indexer: Indexer) {
  def search(
      query: Query = MatchAllQuery(),
      filters: Option[Filters] = None,
      aggs: Option[Aggs] = None,
      fields: List[String] = List("_id"),
      sort: List[SortPredicate] = Nil,
      n: Int = 10
  ): List[String] = {
    searcher
      .search(SearchRequest(query, filters, n, fields.map(FieldName.unsafe), aggs, sort = sort))
      .unsafeRunSync()
      .hits
      .flatMap(_.fields.collect { case TextField(_, value, _) => value })
  }

  def searchRaw(
      query: Query = MatchAllQuery(),
      filters: Option[Filters] = None,
      aggs: Option[Aggs] = None,
      fields: List[String] = List("_id"),
      sort: List[SortPredicate] = Nil,
      n: Int = 10
  ): SearchRoute.SearchResponse = {
    searcher
      .search(SearchRequest(query, filters, n, fields.map(FieldName.unsafe), aggs, sort = sort))
      .unsafeRunSync()
  }

}

object LocalNixie {
  def create(mapping: IndexMapping, inference: InferenceConfig): Resource[IO, LocalNixie] = for {
    metrics  <- Resource.pure(Metrics())
    models   <- Models.create(inference, CacheConfig(), metrics)
    index    <- LocalIndex.create(mapping, LocalStoreConfig(MemoryLocation()), models)
    indexer  <- Indexer.open(index, metrics)
    searcher <- Searcher.open(index, metrics)
  } yield {
    LocalNixie(searcher, indexer)
  }
}

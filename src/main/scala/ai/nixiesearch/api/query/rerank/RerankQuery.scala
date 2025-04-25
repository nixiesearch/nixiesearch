package ai.nixiesearch.api.query.rerank

import ai.nixiesearch.api.SearchRoute
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.core.search.MergedFacetCollector
import ai.nixiesearch.index.Searcher
import ai.nixiesearch.index.Searcher.{Readers, TopDocsWithFacets}
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import org.apache.lucene.search
import org.apache.lucene.search.{IndexSearcher, TopDocs, TopFieldDocs}
import cats.syntax.all.*

trait RerankQuery extends Query {
  def queries: List[Query]
  def combine(docs: List[TopDocs]): IO[TopDocs]

  override def topDocs(
      mapping: IndexMapping,
      readers: Readers,
      sort: List[SearchRoute.SortPredicate],
      filter: Option[Filters],
      encoders: EmbedModelDict,
      aggs: Option[Aggs],
      size: Int
  ): IO[Searcher.TopDocsWithFacets] = for {
    queryTopDocs <- queries.traverse(_.topDocs(mapping, readers, sort, filter, encoders, aggs, size))
    facets       <- IO(MergedFacetCollector(queryTopDocs.map(_.facets), aggs))
    merged       <- combine(queryTopDocs.map(_.docs))
  } yield {
    TopDocsWithFacets(merged, facets)
  }
}

object RerankQuery {
  val supportedTypes = Set("rrf")

  given rerankQueryEncoder: Encoder[RerankQuery] = ???
  given rerankQueryDecoder: Decoder[RerankQuery] = ???
}

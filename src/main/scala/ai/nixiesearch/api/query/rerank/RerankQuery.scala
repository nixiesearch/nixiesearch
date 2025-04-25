package ai.nixiesearch.api.query.rerank

import ai.nixiesearch.api.SearchRoute
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.api.query.retrieve.{
  BoolQuery,
  DisMaxQuery,
  KnnQuery,
  MatchAllQuery,
  MatchQuery,
  MultiMatchQuery,
  SemanticQuery
}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.core.search.MergedFacetCollector
import ai.nixiesearch.index.Searcher
import ai.nixiesearch.index.Searcher.{Readers, TopDocsWithFacets}
import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import org.apache.lucene.search
import org.apache.lucene.search.{IndexSearcher, TopDocs, TopFieldDocs}
import cats.syntax.all.*

trait RerankQuery extends Query {
  def window: Option[Int]
  def queries: List[Query]
  def combine(docs: List[TopDocs], size: Int): IO[TopDocs]

  override def topDocs(
      mapping: IndexMapping,
      readers: Readers,
      sort: List[SearchRoute.SortPredicate],
      filter: Option[Filters],
      encoders: EmbedModelDict,
      aggs: Option[Aggs],
      size: Int
  ): IO[Searcher.TopDocsWithFacets] = for {
    queryTopDocs <- queries.traverse(_.topDocs(mapping, readers, sort, filter, encoders, aggs, window.getOrElse(size)))
    facets       <- IO(MergedFacetCollector(queryTopDocs.map(_.facets), aggs))
    merged       <- combine(queryTopDocs.map(_.docs), size)
  } yield {
    TopDocsWithFacets(merged, facets)
  }
}

object RerankQuery {
  val supportedTypes = Set("rrf")

  given rerankQueryEncoder: Encoder[RerankQuery] = Encoder.instance { case q: RRFQuery =>
    Json.obj("rrf" -> RRFQuery.rrfQueryEncoder(q))
  }
  given rerankQueryDecoder: Decoder[RerankQuery] = Decoder.instance(c =>
    c.value.asObject match {
      case Some(value) =>
        value.keys.toList match {
          case head :: Nil =>
            head match {
              case tpe @ "rrf" => c.downField(tpe).as[RRFQuery]
              case other       => Left(DecodingFailure(s"query type $other not supported", c.history))
            }
          case Nil => Left(DecodingFailure(s"query should contain a type, but got empty object", c.history))
          case other =>
            Left(DecodingFailure(s"query json object should contain exactly one key, but got $other", c.history))
        }
      case None => Left(DecodingFailure(s"query should be a json object, but got ${c.value}", c.history))
    }
  )
}

package ai.nixiesearch.api.query

import ai.nixiesearch.api.SearchRoute.{SearchRequest, SearchResponse}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.IndexReader
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import org.apache.lucene.facet.FacetsCollector
import org.apache.lucene.search.{
  BooleanQuery,
  IndexSearcher,
  MatchAllDocsQuery,
  MultiCollector,
  TopScoreDocCollector,
  Query as LuceneQuery
}

case class MatchAllQuery() extends Query {
  override def search(request: SearchRequest, reader: IndexReader): IO[SearchResponse] = for {
    mapping        <- reader.mapping()
    start          <- IO(System.currentTimeMillis())
    query          <- IO(new MatchAllDocsQuery())
    topCollector   <- IO.pure(TopScoreDocCollector.create(request.size, request.size))
    facetCollector <- IO.pure(new FacetsCollector(false))
    collector      <- IO.pure(MultiCollector.wrap(topCollector, facetCollector))
    _              <- IO(reader.searcher.search(query, collector))
    docs           <- collect(mapping, reader, topCollector.topDocs(), request.fields)
    aggs           <- aggregate(mapping, reader, facetCollector, request.aggs)
    end            <- IO(System.currentTimeMillis())
  } yield {
    SearchResponse(end - start, docs, aggs)
  }

}

object MatchAllQuery {
  implicit val matchAllQueryDecoder: Decoder[MatchAllQuery] = deriveDecoder
  implicit val matchAllQueryEncoder: Encoder[MatchAllQuery] = deriveEncoder
}

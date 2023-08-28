package ai.nixiesearch.index

import ai.nixiesearch.api.SearchRoute.SearchResponse
import ai.nixiesearch.api.aggregation.{Aggregation, Aggs}
import ai.nixiesearch.api.filter.Filter
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.core.aggregator.{AggregationResult, RangeAggregator, TermAggregator}
import ai.nixiesearch.core.codec.DocumentVisitor
import cats.effect.IO
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.facet.FacetsCollector
import org.apache.lucene.store.Directory
import org.apache.lucene.index.IndexReader as LuceneIndexReader
import org.apache.lucene.document.Document as LuceneDocument
import org.apache.lucene.search.{
  BooleanClause,
  BooleanQuery,
  IndexSearcher,
  MatchAllDocsQuery,
  MultiCollector,
  TopDocs,
  TopScoreDocCollector,
  Query as LuceneQuery
}
import cats.implicits.*
import org.apache.lucene.search.BooleanClause.Occur

trait IndexReader extends Logging {
  def config: StoreConfig
  def mapping: IndexMapping
  def reader: LuceneIndexReader
  def dir: Directory
  def searcher: IndexSearcher
  def analyzer: Analyzer

  def aggregate(collector: FacetsCollector, aggs: Aggs): IO[Map[String, AggregationResult]] = {
    aggs.aggs.toList
      .traverse { case (name, agg) =>
        mapping.fields.get(agg.field) match {
          case Some(field) if !field.facet =>
            IO.raiseError(new Exception(s"cannot aggregate over a field marked as a non-facetable"))
          case None => IO.raiseError(new Exception(s"cannot aggregate over a field not defined in schema"))
          case Some(schema) =>
            agg match {
              case a @ Aggregation.TermAggregation(field, size) =>
                TermAggregator.aggregate(reader, a, collector, schema).map(result => name -> result)
              case a @ Aggregation.RangeAggregation(field, ranges) =>
                RangeAggregator.aggregate(reader, a, collector, schema).map(result => name -> result)
            }
        }
      }
      .map(_.toMap)

  }

  def search(query: LuceneQuery, fields: List[String], n: Int, aggs: Aggs): IO[SearchResponse] = for {
    start          <- IO(System.currentTimeMillis())
    topCollector   <- IO.pure(TopScoreDocCollector.create(n, n))
    facetCollector <- IO.pure(new FacetsCollector(false))
    collector      <- IO.pure(MultiCollector.wrap(topCollector, facetCollector))
    _              <- IO(searcher.search(query, collector))
    docs           <- collect(topCollector.topDocs(), fields)
    aggs           <- aggregate(facetCollector, aggs)
    end            <- IO(System.currentTimeMillis())
  } yield {
    SearchResponse(end - start, docs, aggs)
  }

  def search(
      query: Query,
      fields: List[String] = Nil,
      n: Int = 10,
      filters: Filter = Filter(),
      aggs: Aggs = Aggs()
  ): IO[SearchResponse] =
    for {
      compiled     <- query.compile(mapping)
      maybeFilters <- filters.compile(mapping)
      merged <- maybeFilters match {
        case Some(filterQuery) =>
          IO {
            compiled match {
              case _: MatchAllDocsQuery => filterQuery
              case other =>
                val merged = new BooleanQuery.Builder()
                merged.add(new BooleanClause(compiled, Occur.MUST))
                merged.add(new BooleanClause(filterQuery, Occur.FILTER))
                merged.build()
            }
          }
        case None => IO.pure(compiled)
      }
      response <- search(merged, fields, n, aggs)
    } yield {
      response
    }

  def close(): IO[Unit] = info(s"closing index reader for index '${mapping.name}'") *> IO(reader.close())

  private def collect(top: TopDocs, fields: List[String]): IO[List[Document]] = IO {
    val fieldSet = fields.toSet
    val docs = top.scoreDocs.map(doc => {
      val visitor = DocumentVisitor(mapping, fieldSet)
      reader.storedFields().document(doc.doc, visitor)
      visitor.asDocument()
    })
    docs.toList
  }

}

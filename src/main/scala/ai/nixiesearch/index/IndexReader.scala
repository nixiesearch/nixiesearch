package ai.nixiesearch.index

import ai.nixiesearch.api.SearchRoute.SearchResponse
import ai.nixiesearch.api.SuggestRoute.{SuggestResponse, Suggestion}
import ai.nixiesearch.api.aggregation.{Aggregation, Aggs}
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.mapping.{IndexMapping, SuggestMapping}
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.core.aggregate.{AggregationResult, RangeAggregator, TermAggregator}
import ai.nixiesearch.core.codec.{DocumentVisitor, SuggestVisitor}
import ai.nixiesearch.core.nn.model.BiEncoderCache
import cats.effect.{IO, Ref}
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
  def name: String
  def config: StoreConfig
  def mappingRef: Ref[IO, Option[IndexMapping]]
  def reader: LuceneIndexReader
  def dir: Directory
  def searcher: IndexSearcher
  def analyzer: Analyzer
  def encoders: BiEncoderCache

  def mapping(): IO[IndexMapping] = mappingRef.get.flatMap {
    case Some(value) => IO.pure(value)
    case None        => IO.raiseError(new Exception("this should never happen"))
  }

  def aggregate(collector: FacetsCollector, aggs: Aggs): IO[Map[String, AggregationResult]] = for {
    mapping <- mapping()
    result <- aggs.aggs.toList
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

  } yield {
    result
  }

  def searchLucene(query: LuceneQuery, fields: List[String], n: Int, aggs: Aggs): IO[SearchResponse] = for {
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

  def suggest(query: LuceneQuery, n: Int): IO[SuggestResponse] = for {
    start        <- IO(System.currentTimeMillis())
    topCollector <- IO.pure(TopScoreDocCollector.create(n, n))
    _            <- IO(searcher.search(query, topCollector))
    docs         <- collectSuggest(topCollector.topDocs())
  } yield {
    SuggestResponse(docs)
  }

  def close(): IO[Unit] = info(s"closing index reader for index '$name'") *> IO(reader.close())

  private def collect(top: TopDocs, fields: List[String]): IO[List[Document]] = for {
    mapping <- mapping()
  } yield {
    val fieldSet = fields.toSet
    val docs = top.scoreDocs.map(doc => {
      val visitor = DocumentVisitor(mapping, fieldSet)
      reader.storedFields().document(doc.doc, visitor)
      visitor.asDocument(doc.score)
    })
    docs.toList
  }

  private def collectSuggest(top: TopDocs): IO[List[Suggestion]] = IO {
    val docs = top.scoreDocs.flatMap(doc => {
      val visitor = SuggestVisitor(SuggestMapping.SUGGEST_FIELD)
      reader.storedFields().document(doc.doc, visitor)
      visitor.getResult().map(text => Suggestion(text = text, score = doc.score))

    })
    docs.toList
  }

}

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
import org.apache.lucene.index.{DirectoryReader, IndexReader as LuceneIndexReader, IndexWriter as LuceneIndexWriter}
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
  def mappingRef: Ref[IO, IndexMapping]
  def readerRef: Ref[IO, DirectoryReader]
  def searcherRef: Ref[IO, IndexSearcher]
  def writer: LuceneIndexWriter
  def dirtyRef: Ref[IO, Boolean]

//  def aggregate(collector: FacetsCollector, aggs: Aggs): IO[Map[String, AggregationResult]] = for {
//    mapping <- mappingRef.get
//    reader  <- readerRef.get
//    result <- aggs.aggs.toList
//      .traverse { case (name, agg) =>
//        mapping.fields.get(agg.field) match {
//          case Some(field) if !field.facet =>
//            IO.raiseError(new Exception(s"cannot aggregate over a field marked as a non-facetable"))
//          case None => IO.raiseError(new Exception(s"cannot aggregate over a field not defined in schema"))
//          case Some(schema) =>
//            agg match {
//              case a @ Aggregation.TermAggregation(field, size) =>
//                TermAggregator.aggregate(reader, a, collector, schema).map(result => name -> result)
//              case a @ Aggregation.RangeAggregation(field, ranges) =>
//                RangeAggregator.aggregate(reader, a, collector, schema).map(result => name -> result)
//            }
//        }
//      }
//      .map(_.toMap)
//
//  } yield {
//    result
//  }

//  def searchLucene(query: LuceneQuery, fields: List[String], n: Int, aggs: Aggs): IO[SearchResponse] = for {
//    start          <- IO(System.currentTimeMillis())
//    topCollector   <- IO.pure(TopScoreDocCollector.create(n, n))
//    facetCollector <- IO.pure(new FacetsCollector(false))
//    collector      <- IO.pure(MultiCollector.wrap(topCollector, facetCollector))
//    searcher       <- getOrReopenSearcher()
//    _              <- IO(searcher.search(query, collector))
//    docs           <- collect(topCollector.topDocs(), fields)
//    aggs           <- aggregate(facetCollector, aggs)
//    end            <- IO(System.currentTimeMillis())
//  } yield {
//    SearchResponse(end - start, docs, aggs)
//  }

//  private def collect(top: TopDocs, fields: List[String]): IO[List[Document]] = for {
//    reader  <- readerRef.get
//    mapping <- mappingRef.get
//    docs <- IO {
//      val fieldSet = fields.toSet
//      val docs = top.scoreDocs.map(doc => {
//        val visitor = DocumentVisitor(mapping, fieldSet)
//        reader.storedFields().document(doc.doc, visitor)
//        visitor.asDocument(doc.score)
//      })
//      docs.toList
//    }
//  } yield {
//    docs
//  }

  def syncReader(): IO[Unit] = for {
    dirty <- dirtyRef.get // should be atomic
    _ <- dirty match {
      case false => searcherRef.get
      case true =>
        for {
          _           <- info("index contains new writes, reopening reader+searcher")
          oldSearcher <- searcherRef.get
          oldReader   <- readerRef.get
          newReader   <- IO(DirectoryReader.openIfChanged(oldReader, writer))
          newSearcher <- IO(new IndexSearcher(newReader))
          _           <- readerRef.set(newReader)
          _           <- IO(oldReader.close())
          _           <- searcherRef.set(newSearcher)
          _           <- dirtyRef.set(false)
        } yield {}
    }
  } yield {}

}

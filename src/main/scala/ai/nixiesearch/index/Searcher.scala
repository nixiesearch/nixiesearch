package ai.nixiesearch.index

import ai.nixiesearch.api.SearchRoute.{SearchRequest, SearchResponse}
import ai.nixiesearch.api.aggregation.{Aggregation, Aggs}
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.*
import ai.nixiesearch.config.StoreConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.{Document, Logging}
import ai.nixiesearch.core.search.MergedFacetCollector
import ai.nixiesearch.core.search.lucene.*
import cats.effect.{IO, Ref, Resource}
import org.apache.lucene.index.{DirectoryReader, IndexReader, IndexWriter}
import org.apache.lucene.search.{
  IndexSearcher,
  MultiCollector,
  ScoreDoc,
  TopDocs,
  TopScoreDocCollector,
  TotalHits,
  Query as LuceneQuery
}
import cats.implicits.*
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.mapping.SearchType.{HybridSearch, LexicalSearch, SemanticSearch}
import ai.nixiesearch.core.Error.{BackendError, UserError}
import ai.nixiesearch.core.aggregate.{AggregationResult, RangeAggregator, TermAggregator}
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index.Searcher.FieldTopDocs
import ai.nixiesearch.index.manifest.IndexManifest
import ai.nixiesearch.index.sync.ReplicatedIndex
import org.apache.lucene.facet.FacetsCollector
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.TotalHits.Relation
import scala.concurrent.duration.*
import scala.collection.mutable

case class Searcher(
    index: ReplicatedIndex,
    readerRef: Ref[IO, DirectoryReader],
    searcherRef: Ref[IO, IndexSearcher],
    searcherSeqnumRef: Ref[IO, Long]
) extends Logging {

  def name = index.mapping.name

  def sync(): IO[Unit] = for {
    ondiskSeqnum   <- index.seqnum.get
    searcherSeqnum <- searcherSeqnumRef.get
    _ <- IO.whenA(ondiskSeqnum > searcherSeqnum)(for {
      _      <- searcherSeqnumRef.set(ondiskSeqnum)
      reader <- readerRef.updateAndGet(reader => Option(DirectoryReader.openIfChanged(reader)).getOrElse(reader))
      _      <- searcherRef.set(new IndexSearcher(reader))
      _      <- debug(s"index searcher reloaded, seqnum $searcherSeqnum -> $ondiskSeqnum")
    } yield {})
  } yield {}

  def search(request: SearchRequest): IO[SearchResponse] = for {
    start <- IO(System.currentTimeMillis())
    queries <- request.query match {
      case MatchAllQuery() => MatchAllLuceneQuery.create(request.filters, index.mapping)
      case MatchQuery(field, query, operator) =>
        fieldQuery(index.mapping, request.filters, field, query, operator.occur, request.size, index.encoders)
      case MultiMatchQuery(query, fields, operator) =>
        fields
          .traverse(field =>
            fieldQuery(index.mapping, request.filters, field, query, operator.occur, request.size, index.encoders)
          )
          .map(_.flatten)
    }
    searcher      <- searcherRef.get
    fieldTopDocs  <- queries.traverse(query => searchField(searcher, query, request.size))
    mergedFacets  <- IO(MergedFacetCollector(fieldTopDocs.map(_.facets)))
    mergedTopDocs <- reciprocalRank(fieldTopDocs.map(_.docs))
    aggs          <- aggregate(index.mapping, mergedFacets, request.aggs)
    collected     <- collect(index.mapping, mergedTopDocs, request.fields)
    end           <- IO(System.currentTimeMillis())
  } yield {
    SearchResponse(
      took = end - start,
      hits = collected,
      aggs = aggs
    )
  }

  def fieldQuery(
      mapping: IndexMapping,
      filter: Filters,
      field: String,
      query: String,
      operator: Occur,
      size: Int,
      encoders: BiEncoderCache
  ): IO[List[LuceneQuery]] =
    mapping.fields.get(field) match {
      case None => IO.raiseError(UserError(s"Cannot search over undefined field $field"))
      case Some(TextLikeFieldSchema(_, LexicalSearch(language), _, _, _, _)) =>
        LexicalLuceneQuery.create(field, query, filter, language, mapping, operator)
      case Some(TextLikeFieldSchema(_, SemanticSearch(model, prefix), _, _, _, _)) =>
        SemanticLuceneQuery
          .create(
            encoders = encoders,
            model = model,
            prefix = prefix,
            query = query,
            field = field,
            size = size,
            filter = filter,
            mapping = mapping
          )
      case Some(TextLikeFieldSchema(_, HybridSearch(model, prefix, language), _, _, _, _)) =>
        for {
          x1 <- LexicalLuceneQuery.create(field, query, filter, language, mapping, operator)
          x2 <- SemanticLuceneQuery
            .create(
              encoders = encoders,
              model = model,
              prefix = prefix,
              query = query,
              field = field,
              size = size,
              filter = filter,
              mapping = mapping
            )

        } yield {
          x1 ++ x2
        }

      case Some(other) => IO.raiseError(UserError(s"Cannot search over non-text field $field"))
    }

  def searchField(searcher: IndexSearcher, query: LuceneQuery, size: Int): IO[FieldTopDocs] = for {
    topCollector   <- IO.pure(TopScoreDocCollector.create(size, size))
    facetCollector <- IO.pure(new FacetsCollector(false))
    collector      <- IO.pure(MultiCollector.wrap(topCollector, facetCollector))
    _              <- IO(searcher.search(query, collector))
  } yield {
    FieldTopDocs(docs = topCollector.topDocs(), facets = facetCollector)
  }

  case class ShardDoc(docid: Int, shardIndex: Int)

  val K = 60.0f

  def reciprocalRank(topDocs: List[TopDocs]): IO[TopDocs] = topDocs match {
    case head :: Nil => IO.pure(head)
    case Nil         => IO.raiseError(BackendError(s"cannot merge zero query results"))
    case list =>
      IO {
        val docScores = mutable.Map[ShardDoc, Float]()
        for {
          ranking           <- list
          (scoreDoc, index) <- ranking.scoreDocs.zipWithIndex
        } {
          val doc   = ShardDoc(scoreDoc.doc, scoreDoc.shardIndex)
          val score = docScores.getOrElse(doc, 0.0f)
          docScores.put(doc, (score + 1.0f / (K + index)))
        }
        val topDocs = docScores.toList
          .sortBy(-_._2)
          .map { case (doc, score) =>
            new ScoreDoc(doc.docid, score, doc.shardIndex)
          }
          .toArray
        new TopDocs(new TotalHits(topDocs.length, Relation.EQUAL_TO), topDocs)
      }
  }

  def aggregate(
      mapping: IndexMapping,
      collector: FacetsCollector,
      aggs: Aggs
  ): IO[Map[String, AggregationResult]] = for {
    reader <- readerRef.get
    result <- aggs.aggs.toList
      .traverse { case (name, agg) =>
        mapping.fields.get(agg.field) match {
          case Some(field) if !field.facet =>
            IO.raiseError(UserError(s"cannot aggregate over a field marked as a non-facetable"))
          case None => IO.raiseError(UserError(s"cannot aggregate over a field not defined in schema"))
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

  protected def collect(
      mapping: IndexMapping,
      top: TopDocs,
      fields: List[String]
  ): IO[List[Document]] = for {
    reader <- readerRef.get
    docs <- IO {
      val fieldSet = fields.toSet
      val docs = top.scoreDocs.map(doc => {
        val visitor = DocumentVisitor(mapping, fieldSet)
        reader.storedFields().document(doc.doc, visitor)
        visitor.asDocument(doc.score)
      })
      docs.toList
    }
  } yield {
    docs
  }

  def close(): IO[Unit] = for {
    _ <- info(s"closing index $name")
    _ <- readerRef.get.flatMap(reader => IO(reader.close()))
  } yield {}
}

object Searcher extends Logging {
  case class FieldTopDocs(docs: TopDocs, facets: FacetsCollector)
  def open(index: ReplicatedIndex): Resource[IO, Searcher] = {
    for {
      reader      <- Resource.eval(IO(DirectoryReader.open(index.directory)))
      readerRef   <- Resource.eval(Ref.of[IO, DirectoryReader](reader))
      searcher    <- Resource.eval(IO(new IndexSearcher(reader)))
      searcherRef <- Resource.eval(Ref.of[IO, IndexSearcher](searcher))
      diskSeqnum  <- Resource.eval(index.seqnum.get)
      versionRef  <- Resource.eval(Ref.of[IO, Long](diskSeqnum))
      _           <- Resource.eval(info(s"opened index ${index.name} version=$diskSeqnum"))
      searcher    <- Resource.make(IO.pure(Searcher(index, readerRef, searcherRef, versionRef)))(s => s.close())
      _           <- fs2.Stream.repeatEval(searcher.sync()).metered(1.second).compile.drain.background
    } yield {
      searcher
    }

  }

}

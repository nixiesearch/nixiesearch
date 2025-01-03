package ai.nixiesearch.index

import ai.nixiesearch.api.SearchRoute.{
  RAGRequest,
  RAGResponse,
  SearchRequest,
  SearchResponse,
  SuggestRequest,
  SuggestResponse
}
import ai.nixiesearch.api.aggregation.{Aggregation, Aggs}
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.*
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.{Document, Field, Logging}
import ai.nixiesearch.core.search.MergedFacetCollector
import ai.nixiesearch.core.search.lucene.*
import ai.nixiesearch.core.field.TextField
import cats.effect.{IO, Ref, Resource}
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.{
  IndexSearcher,
  MultiCollectorManager,
  ScoreDoc,
  TopDocs,
  TopScoreDocCollectorManager,
  TotalHits,
  Query as LuceneQuery
}
import cats.implicits.*
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.mapping.SearchType.{HybridSearch, LexicalSearch, SemanticSearch}
import ai.nixiesearch.core.Error.{BackendError, UserError}
import ai.nixiesearch.core.aggregate.{AggregationResult, RangeAggregator, TermAggregator}
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.core.suggest.{GeneratedSuggestions, SuggestionRanker}
import ai.nixiesearch.index.Searcher.{FieldTopDocs, Readers}
import ai.nixiesearch.index.sync.Index
import ai.nixiesearch.util.{DurationStream, StreamMark}
import org.apache.lucene.facet.{FacetsCollector, FacetsCollectorManager}
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.TotalHits.Relation
import org.apache.lucene.search.suggest.document.SuggestIndexSearcher
import fs2.Stream
import scala.collection.mutable
import language.experimental.namedTuples

case class Searcher(index: Index, readersRef: Ref[IO, Option[Readers]]) extends Logging {

  def name = index.mapping.name

  def sync(): IO[Unit] = for {
    readers      <- getReadersOrFail()
    ondiskSeqnum <- index.seqnum.get
    _ <- IO.whenA(ondiskSeqnum > readers.seqnum)(for {
      newReaders <- Readers.reopen(readers.reader, ondiskSeqnum)
      _          <- readersRef.set(Some(newReaders))
      _          <- info(s"index searcher reloaded, seqnum ${readers.seqnum} -> $ondiskSeqnum")
    } yield {})
  } yield {}

  def suggest(request: SuggestRequest): IO[SuggestResponse] = for {
    start     <- IO(System.currentTimeMillis())
    suggester <- getReadersOrFail().map(_.suggester)
    fieldSuggestions <- Stream
      .emits(request.fields)
      .evalMap(fieldName =>
        index.mapping.fields.get(fieldName) match {
          case None => IO.raiseError(UserError(s"field '$fieldName' is not found in mapping"))
          case Some(TextLikeFieldSchema(language=language, suggest=Some(schema))) =>
            GeneratedSuggestions.fromField(fieldName, suggester, language.analyzer, request.query, request.count)
          case Some(TextLikeFieldSchema(language=language, suggest=None)) =>
            IO.raiseError(UserError(s"field '$fieldName' is not suggestable in mapping"))
          case Some(other) => IO.raiseError(UserError(s"cannot generate suggestions over field $other"))

        }
      )
      .compile
      .toList
    ranked <- SuggestionRanker().rank(fieldSuggestions, request)
  } yield {
    SuggestResponse(
      suggestions = ranked,
      took = System.currentTimeMillis() - start
    )
  }

  def search(request: SearchRequest): IO[SearchResponse] = for {
    start <- IO(System.currentTimeMillis())
    queries <- request.query match {
      case MatchAllQuery() => MatchAllLuceneQuery.create(request.filters, index.mapping)
      case MatchQuery(field, query, operator) =>
        fieldQuery(index.mapping, request.filters, field, query, operator.occur, request.size, index.models.embedding)
      case MultiMatchQuery(query, fields, operator) =>
        fields
          .traverse(field =>
            fieldQuery(
              index.mapping,
              request.filters,
              field,
              query,
              operator.occur,
              request.size,
              index.models.embedding
            )
          )
          .map(_.flatten)
    }
    searcher      <- getReadersOrFail().map(_.searcher)
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
      aggs = aggs,
      ts = end
    )
  }

  def rag(docs: List[Document], request: RAGRequest): Stream[IO, RAGResponse] = {
    val stream = for {
      prompt <- Stream.eval(IO(s"${request.prompt}\n\n${docs
          .take(request.topDocs)
          .map(doc =>
            doc.fields
              .collect {
                case TextField(name, value) if request.fields.contains(name) || request.fields.isEmpty =>
                  s"$name: $value"
              }
              .mkString("\n")
          )
          .mkString("\n\n")}"))
      _     <- Stream.eval(debug(s"prompt: ${prompt}"))
      start <- Stream.eval(IO(System.currentTimeMillis()))
      (token, took) <- index.models.generative
        .generate(request.model, prompt, request.maxResponseLength)
        .through(DurationStream.pipe(start))
      now <- Stream.eval(IO(System.currentTimeMillis()))
    } yield {
      RAGResponse(token = token, ts = now, took = took, last = false)
    }
    stream.through(StreamMark.pipe[RAGResponse](tail = tok => tok.copy(last = true)))
  }

  def fieldQuery(
      mapping: IndexMapping,
      filter: Option[Filters],
      field: String,
      query: String,
      operator: Occur,
      size: Int,
      encoders: EmbedModelDict
  ): IO[List[LuceneQuery]] =
    mapping.fields.get(field) match {
      case None => IO.raiseError(UserError(s"Cannot search over undefined field $field"))
      case Some(TextLikeFieldSchema(search=LexicalSearch(),language=language)) =>
        LexicalLuceneQuery.create(field, query, filter, language, mapping, operator)
      case Some(TextLikeFieldSchema(search=SemanticSearch(modelRef))) =>
        SemanticLuceneQuery
          .create(
            encoders = encoders,
            model = modelRef,
            query = query,
            field = field,
            size = size,
            filter = filter,
            mapping = mapping
          )

      case Some(TextLikeFieldSchema(search=HybridSearch(modelRef), language=language)) =>
        for {
          x1 <- LexicalLuceneQuery.create(field, query, filter, language, mapping, operator)
          x2 <- SemanticLuceneQuery
            .create(
              encoders = encoders,
              model = modelRef,
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
    topCollector   <- IO.pure(new TopScoreDocCollectorManager(size, size))
    facetCollector <- IO.pure(new FacetsCollectorManager())
    collector      <- IO.pure(new MultiCollectorManager(topCollector, facetCollector))
    results        <- IO(searcher.search(query, collector))
  } yield {
    FieldTopDocs(docs = results(0).asInstanceOf[TopDocs], facets = results(1).asInstanceOf[FacetsCollector])
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
      aggs: Option[Aggs]
  ): IO[Map[String, AggregationResult]] = for {
    reader <- getReadersOrFail().map(_.reader)

    result <- aggs match {
      case None => IO(Map.empty)
      case Some(a) =>
        a.aggs.toList
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
    }
  } yield {
    result
  }

  protected def collect(
      mapping: IndexMapping,
      top: TopDocs,
      fields: List[String]
  ): IO[List[Document]] = for {
    reader <- getReadersOrFail().map(_.reader)
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

  def getReadersOrFail(): IO[Readers] = {
    readersRef.get.flatMap {
      case None =>
        Readers.attemptOpenIfExists(index).flatMap {
          case None =>
            IO.raiseError(BackendError(s"index '${index.name.value}' does not yet have an index with documents"))
          case Some(opened) =>
            readersRef.set(Some(opened)) *> IO.pure(opened)
        }
      case Some(result) => IO.pure(result)
    }
  }

  def close(): IO[Unit] = readersRef.get.flatMap {
    case Some(readers) => readers.close() *> info(s"closed index reader for index ${index.name.value}")
    case None          => info(s"index '${index.name.value} does not have a reader open, there's nothing to close'")
  }

}

object Searcher extends Logging {
  case class Readers(reader: DirectoryReader, searcher: IndexSearcher, suggester: SuggestIndexSearcher, seqnum: Long) {
    def close(): IO[Unit] = IO(reader.close())
  }
  object Readers {
    def attemptOpenIfExists(index: Index): IO[Option[Readers]] = {
      IO(DirectoryReader.indexExists(index.directory)).flatMap {
        case false => info(s"index '${index.name.value}' does not yet exist") *> IO.none
        case true =>
          for {
            reader     <- IO(DirectoryReader.open(index.directory))
            searcher   <- IO(new IndexSearcher(reader))
            suggester  <- IO(new SuggestIndexSearcher(reader))
            diskSeqnum <- index.seqnum.get
            _          <- info(s"opened index reader for index '${index.name.value}', seqnum=${diskSeqnum}")
          } yield {
            Some(Readers(reader, searcher, suggester, diskSeqnum))
          }

      }
    }

    def reopen(oldReader: DirectoryReader, newSeqnum: Long): IO[Readers] = for {
      readerOption <- IO(Option(DirectoryReader.openIfChanged(oldReader)))
      reader <- readerOption match {
        case None => IO.pure(oldReader)
        case Some(newReader) =>
          debug(s"reopening reader, seqnum=$newSeqnum") *> IO(oldReader.close()) *> IO.pure(newReader)
      }
      searcher  <- IO(new IndexSearcher(reader))
      suggester <- IO(new SuggestIndexSearcher(reader))
    } yield {
      Readers(reader, searcher, suggester, newSeqnum)
    }
  }

  case class FieldTopDocs(docs: TopDocs, facets: FacetsCollector)
  def open(index: Index): Resource[IO, Searcher] = {
    for {

      _          <- Resource.eval(info(s"opening index ${index.name.value}"))
      readersRef <- Resource.eval(Ref.of[IO, Option[Readers]](None))
      searcher   <- Resource.make(IO.pure(Searcher(index, readersRef)))(s => s.close())
    } yield {
      searcher
    }

  }

}

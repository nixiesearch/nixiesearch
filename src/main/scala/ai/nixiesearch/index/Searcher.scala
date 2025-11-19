package ai.nixiesearch.index

import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue.{First, Last}
import ai.nixiesearch.api.SearchRoute.SortPredicate.SortOrder.{ASC, DESC, Default}
import ai.nixiesearch.api.SearchRoute.{
  RAGRequest,
  RAGResponse,
  SearchRequest,
  SearchResponse,
  SortPredicate,
  SuggestRequest,
  SuggestResponse,
  TookTime
}
import ai.nixiesearch.api.SearchRoute.SortPredicate.{DistanceSort, FieldValueSort, MissingValue}
import ai.nixiesearch.api.aggregation.{Aggregation, Aggs}
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.*
import ai.nixiesearch.api.query.rerank.RRFQuery
import ai.nixiesearch.api.query.retrieve.{KnnQuery, MatchAllQuery, MatchQuery, MultiMatchQuery, SemanticQuery}
import ai.nixiesearch.config.mapping.{FieldName, IndexMapping}
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.{Document, Field, Logging}
import ai.nixiesearch.core.search.MergedFacetCollector
import ai.nixiesearch.core.field.{FieldCodec, *}
import cats.effect.{IO, Ref, Resource}
import org.apache.lucene.index.{DirectoryReader, DocValues, IndexReader, LeafReaderContext, ReaderUtil}
import org.apache.lucene.search.{
  DocIdSetIterator,
  IndexSearcher,
  MultiCollectorManager,
  ScoreDoc,
  Sort,
  SortField,
  TopDocs,
  TopFieldCollectorManager,
  TopScoreDocCollectorManager,
  TotalHits,
  Query as LuceneQuery
}
import cats.syntax.all.*
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.core.Error.{BackendError, UserError}
import ai.nixiesearch.core.Field.{FloatField, IdField, TextField}
import ai.nixiesearch.core.aggregate.{AggregationResult, RangeAggregator, TermAggregator}
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.metrics.{Metrics, SearchMetrics}
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.core.suggest.{GeneratedSuggestions, SuggestionRanker}
import ai.nixiesearch.index.Searcher.{Readers, TopDocsWithFacets}
import ai.nixiesearch.index.sync.Index
import ai.nixiesearch.util.{DurationStream, StreamMark}
import org.apache.lucene.facet.{FacetsCollector, FacetsCollectorManager}
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.TotalHits.Relation
import org.apache.lucene.search.suggest.document.SuggestIndexSearcher
import fs2.Stream
import org.apache.lucene.document.LatLonDocValuesField

import java.util.concurrent.Executors
import scala.jdk.CollectionConverters.*
import scala.collection.mutable

case class Searcher(index: Index, readersRef: Ref[IO, Option[Readers]], metrics: Metrics) extends Logging {

  def name = index.mapping.name

  def sync(): IO[Unit] = for {
    readers      <- getReadersOrFail()
    ondiskSeqnum <- index.seqnum.get
    _            <- IO.whenA(ondiskSeqnum > readers.seqnum)(for {
      newReaders <- Readers.reopen(readers.reader, ondiskSeqnum)
      _          <- readersRef.set(Some(newReaders))
      _          <- info(s"index searcher reloaded, seqnum ${readers.seqnum} -> $ondiskSeqnum")
    } yield {})
  } yield {}

  def suggest(request: SuggestRequest): IO[SuggestResponse] = for {
    start            <- IO(System.currentTimeMillis())
    _                <- IO(metrics.search.activeQueries.labelValues(index.name.value).inc())
    suggester        <- getReadersOrFail().map(_.suggester)
    fieldSuggestions <- Stream
      .emits(request.fields)
      .evalMap(fieldName =>
        index.mapping.fieldSchema(StringName(fieldName)) match {
          case None => IO.raiseError(UserError(s"field '$fieldName' is not found in mapping"))
          case Some(TextLikeFieldSchema(suggest = Some(schema))) =>
            GeneratedSuggestions.fromField(fieldName, suggester, schema.analyze.analyzer, request.query, request.count)
          case Some(TextLikeFieldSchema(suggest = None)) =>
            IO.raiseError(UserError(s"field '$fieldName' is not suggestable in mapping"))
          case Some(other) => IO.raiseError(UserError(s"cannot generate suggestions over field $other"))

        }
      )
      .compile
      .toList
    ranked <- SuggestionRanker().rank(fieldSuggestions, request)
    end    <- IO(System.currentTimeMillis())
    _      <- IO(metrics.search.suggestTotal.labelValues(index.name.value).inc())
    _      <- IO(metrics.search.suggestTimeSeconds.labelValues(index.name.value).inc((end - start) / 1000.0))
    _      <- IO(metrics.search.activeQueries.labelValues(index.name.value).dec())

  } yield {
    SuggestResponse(
      suggestions = ranked,
      took = 0.0f // filled later
    )
  }

  def search(request: SearchRequest): IO[SearchResponse] = for {
    start   <- IO(System.nanoTime())
    _       <- IO(metrics.search.activeQueries.labelValues(index.name.value).inc())
    readers <- getReadersOrFail()
    openEnd <- IO(System.nanoTime())
    query   <- request.query match {
      case q @ KnnQuery(k = None)               => IO.pure(q.copy(k = Some(request.size)))
      case q @ SemanticQuery(k = None)          => IO.pure(q.copy(k = Some(request.size)))
      case q @ RRFQuery(window = None)          => IO.pure(q.copy(window = Some(request.size)))
      case q: RRFQuery if request.sort.nonEmpty => IO.raiseError(UserError("RRF query does not support sorting"))
      case other                                => IO.pure(other)
    }
    mergedTopDocs <- query.topDocs(
      mapping = index.mapping,
      readers = readers,
      sort = request.sort,
      filter = request.filters,
      models = index.models,
      aggs = request.aggs,
      size = request.size
    )
    aggStart  <- IO(System.nanoTime())
    aggs      <- aggregate(index.mapping, mergedTopDocs.facets, request.aggs)
    aggEnd    <- IO(System.nanoTime())
    collected <- collect(index.mapping, mergedTopDocs.docs, request.fields)
    end       <- IO(System.nanoTime())
    _         <- IO(metrics.search.searchTimeSeconds.labelValues(index.name.value).inc((end - start) / 1000.0))
    _         <- IO(metrics.search.searchTotal.labelValues(index.name.value).inc())
    _         <- IO(metrics.search.activeQueries.labelValues(index.name.value).dec())
  } yield {
    SearchResponse(
      took = mergedTopDocs.took.copy(
        open = Some((openEnd - start) / 1000000000.0),
        agg = Some((aggEnd - aggStart) / 1000000000.0),
        fetch = Some((end - aggEnd) / 1000000000.0)
      ),
      hits = collected,
      aggs = aggs,
      ts = end / 1000000L
    )
  }

  def rag(docs: List[Document], request: RAGRequest): Stream[IO, RAGResponse] = {
    val start  = System.currentTimeMillis()
    val stream = for {
      _ <- Stream.eval(IO(metrics.search.activeQueries.labelValues(index.name.value).inc()))

      prompt <- Stream.eval(
        index.models.generative.prompt(
          request.model,
          request.prompt,
          docs.take(request.topDocs),
          request.maxDocLength,
          request.fields
        )
      )
      _             <- Stream.eval(debug(s"prompt: ${prompt}"))
      (token, took) <- index.models.generative
        .generate(request.model, prompt, request.maxResponseLength)
        .through(DurationStream.pipe(start))
      now <- Stream.eval(IO(System.currentTimeMillis()))
    } yield {
      RAGResponse(token = token, ts = now, took = took, last = false)
    }
    stream
      .through(StreamMark.pipe[RAGResponse](tail = tok => tok.copy(last = true)))
      .onFinalize(for {
        end <- IO(System.currentTimeMillis())
        _   <- IO(metrics.search.ragTimeSeconds.labelValues(index.name.value).inc((end - start) / 1000.0))
        _   <- IO(metrics.search.ragTotal.labelValues(index.name.value).inc())
        _   <- IO(metrics.search.activeQueries.labelValues(index.name.value).dec())
      } yield {})
  }

  def aggregate(
      mapping: IndexMapping,
      collector: FacetsCollector,
      aggs: Option[Aggs]
  ): IO[Map[String, AggregationResult]] = for {
    reader <- getReadersOrFail().map(_.reader)

    result <- aggs match {
      case None    => IO(Map.empty)
      case Some(a) =>
        a.aggs.toList
          .traverse { case (name, agg) =>
            mapping.fieldSchema(StringName(agg.field)) match {
              case Some(field) if !field.facet =>
                IO.raiseError(UserError(s"cannot aggregate over a field marked as a non-facetable"))
              case None         => IO.raiseError(UserError(s"cannot aggregate over a field not defined in schema"))
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

  val defaultFields                                                                   = Set("_id", "_score")
  protected def collect(mapping: IndexMapping, top: TopDocs, fields: List[FieldName]) = for {
    reader <- getReadersOrFail().map(_.reader)
    result <- (fields.forall(f => defaultFields.contains(f.name)) && hasBinaryDv(reader)) match {
      case true  => collectFastIdOnly(reader, top)
      case false => collectStored(reader, mapping, top, fields)
    }
  } yield {
    result
  }

  val ID_DOCVALUE_NAME                                        = "_id" + FieldCodec.FILTER_SUFFIX
  protected def hasBinaryDv(reader: DirectoryReader): Boolean = {
    if (reader.leaves().isEmpty) {
      false
    } else {
      val dv = DocValues.getBinary(reader.leaves().getFirst.reader(), ID_DOCVALUE_NAME)
      dv.nextDoc() != DocIdSetIterator.NO_MORE_DOCS
    }
  }

  /** A fast-path for _id only fetching. Yes benchmaxxing.
    * @return
    */
  protected def collectFastIdOnly(reader: DirectoryReader, top: TopDocs): IO[List[Document]] = IO {
    val segments = reader.leaves()
    top.scoreDocs
      .map(scoreDoc => {
        val segmentIndex = ReaderUtil.subIndex(scoreDoc.doc, segments)
        val leafReader   = segments.get(segmentIndex)
        val idDocValues  = DocValues.getBinary(leafReader.reader(), ID_DOCVALUE_NAME)
        idDocValues.advance(scoreDoc.doc - leafReader.docBase)
        val id = idDocValues.binaryValue().utf8ToString()
        Document(IdField("_id", id), FloatField("_score", scoreDoc.score))
      })
      .toList
  }

  protected def collectStored(
      reader: DirectoryReader,
      mapping: IndexMapping,
      top: TopDocs,
      fields: List[FieldName]
  ): IO[List[Document]] = for {
    visitor <- DocumentVisitor.create(mapping, fields)
    docs    <- top.scoreDocs.toList.traverse(collectOne(reader, visitor, _))
  } yield {
    docs
  }

  private def collectOne(reader: DirectoryReader, visitor: DocumentVisitor, doc: ScoreDoc): IO[Document] = for {
    _      <- IO(visitor.reset())
    _      <- IO(reader.storedFields().document(doc.doc, visitor))
    stored <- IO(visitor.asDocument(doc.score)).flatMap {
      case Left(value)  => IO.raiseError(value)
      case Right(value) => IO.pure(value)
    }
  } yield {
    stored
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
    case None          => info(s"Index '${index.name.value}' does not have a reader open, there is nothing to close.")
  }

}

object Searcher extends Logging {
  case class Readers(
      reader: DirectoryReader,
      searcher: IndexSearcher,
      suggester: SuggestIndexSearcher,
      seqnum: Long,
      fields: List[String]
  ) {
    def close(): IO[Unit] = IO(reader.close())
  }
  object Readers {
    def attemptOpenIfExists(index: Index): IO[Option[Readers]] = {
      IO(DirectoryReader.indexExists(index.directory)).flatMap {
        case false => info(s"index '${index.name.value}' does not yet exist") *> IO.none
        case true  =>
          for {
            reader     <- IO(DirectoryReader.open(index.directory))
            searcher   <- IO(new IndexSearcher(reader, Executors.newWorkStealingPool()))
            suggester  <- IO(new SuggestIndexSearcher(reader))
            diskSeqnum <- index.seqnum.get
            fields     <- IO(getFields(searcher.getIndexReader))
            _          <- info(s"opened index reader for index '${index.name.value}', seqnum=${diskSeqnum}")
          } yield {
            Some(Readers(reader, searcher, suggester, diskSeqnum, fields))
          }

      }
    }

    def reopen(oldReader: DirectoryReader, newSeqnum: Long): IO[Readers] = for {
      readerOption <- IO(Option(DirectoryReader.openIfChanged(oldReader)))
      reader       <- readerOption match {
        case None            => IO.pure(oldReader)
        case Some(newReader) =>
          debug(s"reopening reader, seqnum=$newSeqnum") *> IO(oldReader.close()) *> IO.pure(newReader)
      }
      searcher  <- IO(new IndexSearcher(reader))
      suggester <- IO(new SuggestIndexSearcher(reader))
      fields    <- IO(getFields(searcher.getIndexReader))
    } yield {
      Readers(reader, searcher, suggester, newSeqnum, fields)
    }

    def getFields(reader: IndexReader): List[String] = {
      reader.leaves().asScala.toList.flatMap(_.reader().getFieldInfos.iterator().asScala.map(_.name)).distinct
    }
  }

  case class TopDocsWithFacets(docs: TopDocs, facets: FacetsCollector, took: TookTime)
  def open(index: Index, metrics: Metrics): Resource[IO, Searcher] = {
    for {

      _          <- Resource.eval(info(s"opening index ${index.name.value}"))
      readersRef <- Resource.eval(Ref.of[IO, Option[Readers]](None))
      searcher   <- Resource.make(IO.pure(Searcher(index, readersRef, metrics)))(s => s.close())
    } yield {
      searcher
    }

  }

}

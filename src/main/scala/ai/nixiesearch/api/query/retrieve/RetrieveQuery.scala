package ai.nixiesearch.api.query.retrieve

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.*
import ai.nixiesearch.api.SearchRoute.SortPredicate.SortOrder.*
import ai.nixiesearch.api.aggregation.Aggs
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.mapping.{FieldName, IndexMapping}
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.field.*
import ai.nixiesearch.index.{Models, Searcher}
import ai.nixiesearch.index.Searcher.{Readers, TopDocsWithFacets}
import ai.nixiesearch.config.mapping.FieldName.*
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import org.apache.lucene.facet.{FacetsCollector, FacetsCollectorManager}
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.{
  BooleanClause,
  BooleanQuery,
  IndexSearcher,
  MultiCollectorManager,
  Sort,
  SortField,
  TopDocs,
  TopFieldCollectorManager,
  TopScoreDocCollectorManager
}

trait RetrieveQuery extends Query {
  def compile(
      mapping: IndexMapping,
      maybeFilter: Option[Filters],
      encoders: EmbedModelDict,
      fields: List[String]
  ): IO[org.apache.lucene.search.Query]

  def applyFilters(
      mapping: IndexMapping,
      luceneQuery: org.apache.lucene.search.Query,
      maybeFilter: Option[Filters]
  ): IO[org.apache.lucene.search.Query] = maybeFilter match {
    case Some(filter) =>
      filter.toLuceneQuery(mapping).map {
        case None              => luceneQuery
        case Some(filterQuery) =>
          val outerQuery = new BooleanQuery.Builder()
          outerQuery.add(new BooleanClause(filterQuery, Occur.FILTER))
          outerQuery.add(new BooleanClause(luceneQuery, Occur.MUST))
          outerQuery.build()
      }
    case None => IO.pure(luceneQuery)
  }

  def expandFields(candidates: List[FieldName], all: Set[String]): List[String] = {
    candidates.flatMap {
      case s: StringName if all.contains(s.name) => List(s.name)
      case w: WildcardName                       => all.filter(f => w.matches(f))
      case _                                     => Nil
    }
  }

  override def topDocs(
      mapping: IndexMapping,
      readers: Readers,
      sort: List[SortPredicate],
      maybeFilter: Option[Filters],
      models: Models,
      aggs: Option[Aggs],
      size: Int
  ): IO[TopDocsWithFacets] = for {
    luceneQueryWithFilters <- compile(mapping, maybeFilter, models.embedding, readers.fields)
    topCollector           <- sort match {
      case Nil => IO.pure(new TopScoreDocCollectorManager(size, size))
      case nel =>
        nel
          .map(pred => makeSortField(mapping, pred))
          .sequence
          .map(sorts => new TopFieldCollectorManager(new Sort(sorts*), size, null, Integer.MAX_VALUE))
    }
    facetCollector <- IO.pure(new FacetsCollectorManager())
    collector      <- IO.pure(new MultiCollectorManager(topCollector, facetCollector))
    results        <- IO(readers.searcher.search(luceneQueryWithFilters, collector))
  } yield {
    TopDocsWithFacets(docs = results(0).asInstanceOf[TopDocs], facets = results(1).asInstanceOf[FacetsCollector])
  }

  def makeSortField(mapping: IndexMapping, by: SortPredicate): IO[SortField] = {
    val reverse = by match {
      case FieldValueSort(StringName("_score"), Default, _) => false
      case FieldValueSort(StringName("_score"), ASC, _)     => true
      case FieldValueSort(StringName("_score"), DESC, _)    => false
      case FieldValueSort(_, ASC, _)                        => false
      case FieldValueSort(_, Default, _)                    => false
      case FieldValueSort(_, DESC, _)                       => true
      case DistanceSort(_, _, _)                            => false
    }
    val missing = by match {
      case FieldValueSort(_, _, missing) => missing
      case DistanceSort(_, _, _)         => MissingValue.Last
    }
    mapping.fieldSchema(by.field.name) match {
      case Some(schema) if !schema.sort =>
        IO.raiseError(UserError(s"cannot sort by field '${by.field.name}: it's not sortable in index schema'"))
      case Some(s: IdFieldSchema)         => IO.pure(IdField.sort(reverse))
      case Some(s: IntFieldSchema)        => IO.pure(IntField.sort(s.name, reverse, missing))
      case Some(s: IntListFieldSchema)    => IO.raiseError(UserError("sorting by int[] is not yet supported"))
      case Some(s: BooleanFieldSchema)    => IO.pure(BooleanField.sort(s.name, reverse, missing))
      case Some(s: DateFieldSchema)       => IO.pure(DateField.sort(s.name, reverse, missing))
      case Some(s: LongFieldSchema)       => IO.pure(LongField.sort(s.name, reverse, missing))
      case Some(s: LongListFieldSchema)   => IO.raiseError(UserError("sorting by long[] is not yet supported"))
      case Some(s: DateTimeFieldSchema)   => IO.pure(DateTimeField.sort(s.name, reverse, missing))
      case Some(s: FloatFieldSchema)      => IO.pure(FloatField.sort(s.name, reverse, missing))
      case Some(s: FloatListFieldSchema)  => IO.raiseError(UserError("sorting by float[] is not yet supported"))
      case Some(s: DoubleFieldSchema)     => IO.pure(DoubleField.sort(s.name, reverse, missing))
      case Some(s: DoubleListFieldSchema) => IO.raiseError(UserError("sorting by double[] is not yet supported"))
      case Some(s: TextFieldSchema)       => IO.pure(TextField.sort(s.name, reverse, missing))
      case Some(s: TextListFieldSchema)   => IO.pure(TextListField.sort(s.name, reverse, missing))
      case Some(s: GeopointFieldSchema)   =>
        by match {
          case _: FieldValueSort =>
            IO.raiseError(UserError(s"to sort by a geopoint, you need to pass lat and lon coordinates"))
          case DistanceSort(field, lat, lon) =>
            IO.pure(GeopointField.sort(field, lat, lon))
        }

      case None if by.field.name == "_score" => IO.pure(new SortField(by.field.name, SortField.Type.SCORE, reverse))
      case None if by.field.name == "_doc"   => IO.pure(new SortField(by.field.name, SortField.Type.DOC, reverse))
      case None                              =>
        val fieldNames = mapping.fields.keys.map(_.name).toList
        IO.raiseError(
          UserError(s"cannot sort by '${by.field.name}' as it's missing in index schema (fields=$fieldNames)")
        )
    }

  }

}

object RetrieveQuery {
  given retrieveQueryEncoder: Encoder[RetrieveQuery] = Encoder.instance {
    case q: MatchAllQuery   => Json.obj("match_all" -> MatchAllQuery.matchAllQueryEncoder(q))
    case q: BoolQuery       => Json.obj("bool" -> BoolQuery.boolQueryEncoder(q))
    case q: DisMaxQuery     => Json.obj("dis_max" -> DisMaxQuery.disMaxQueryEncoder(q))
    case q: KnnQuery        => Json.obj("knn" -> KnnQuery.knnQueryEncoder(q))
    case q: MatchQuery      => Json.obj("match" -> MatchQuery.matchQueryEncoder(q))
    case q: MultiMatchQuery => Json.obj("multi_match" -> MultiMatchQuery.multiMatchQueryEncoder(q))
    case q: SemanticQuery   => Json.obj("semantic" -> SemanticQuery.semanticQueryEncoder(q))
  }

  def supportedTypes = Set("match_all", "bool", "dis_max", "knn", "match", "multi_match", "semantic")

  given retrieveQueryDecoder: Decoder[RetrieveQuery] = Decoder.instance(c =>
    c.value.asObject match {
      case Some(value) =>
        value.keys.toList match {
          case head :: Nil =>
            head match {
              case tpe @ "match_all"   => c.downField(tpe).as[MatchAllQuery]
              case tpe @ "bool"        => c.downField(tpe).as[BoolQuery]
              case tpe @ "dis_max"     => c.downField(tpe).as[DisMaxQuery]
              case tpe @ "knn"         => c.downField(tpe).as[KnnQuery]
              case tpe @ "match"       => c.downField(tpe).as[MatchQuery]
              case tpe @ "multi_match" => c.downField(tpe).as[MultiMatchQuery]
              case tpe @ "semantic"    => c.downField(tpe).as[SemanticQuery]
              case other               => Left(DecodingFailure(s"query type $other not supported", c.history))
            }
          case Nil   => Left(DecodingFailure(s"query should contain a type, but got empty object", c.history))
          case other =>
            Left(DecodingFailure(s"query json object should contain exactly one key, but got $other", c.history))
        }
      case None => Left(DecodingFailure(s"query should be a json object, but got ${c.value}", c.history))
    }
  )

}

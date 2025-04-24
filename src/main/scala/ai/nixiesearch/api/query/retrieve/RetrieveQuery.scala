package ai.nixiesearch.api.query.retrieve

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.*
import ai.nixiesearch.api.SearchRoute.SortPredicate.SortOrder.*
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.field.*
import ai.nixiesearch.index.Searcher
import ai.nixiesearch.index.Searcher.TopDocsWithFacets
import ai.nixiesearch.config.mapping.FieldName.*
import cats.effect.IO
import org.apache.lucene.facet.{FacetsCollector, FacetsCollectorManager}
import org.apache.lucene.search.{
  IndexSearcher,
  MultiCollectorManager,
  Sort,
  SortField,
  TopDocs,
  TopFieldCollectorManager,
  TopScoreDocCollectorManager
}

trait RetrieveQuery extends Query {
  def compile(mapping: IndexMapping, filter: Option[Filters]): IO[org.apache.lucene.search.Query]

  override def topDocs(
      mapping: IndexMapping,
      searcher: IndexSearcher,
      sort: List[SortPredicate],
      filter: Option[Filters],
      size: Int
  ): IO[Searcher.TopDocsWithFacets] = for {
    query <- compile(mapping, filter)
    topCollector <- sort match {
      case Nil => IO.pure(new TopScoreDocCollectorManager(size, size))
      case nel =>
        nel
          .map(pred => makeSortField(mapping, pred))
          .sequence
          .map(sorts => new TopFieldCollectorManager(new Sort(sorts*), size, null, Integer.MAX_VALUE))
    }
    facetCollector <- IO.pure(new FacetsCollectorManager())
    collector      <- IO.pure(new MultiCollectorManager(topCollector, facetCollector))
    results        <- IO(searcher.search(query, collector))

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
      case Some(s: IntFieldSchema)      => IO.pure(IntField.sort(s.name, reverse, missing))
      case Some(s: BooleanFieldSchema)  => IO.pure(BooleanField.sort(s.name, reverse, missing))
      case Some(s: DateFieldSchema)     => IO.pure(DateField.sort(s.name, reverse, missing))
      case Some(s: LongFieldSchema)     => IO.pure(LongField.sort(s.name, reverse, missing))
      case Some(s: DateTimeFieldSchema) => IO.pure(DateTimeField.sort(s.name, reverse, missing))
      case Some(s: FloatFieldSchema)    => IO.pure(FloatField.sort(s.name, reverse, missing))
      case Some(s: DoubleFieldSchema)   => IO.pure(DoubleField.sort(s.name, reverse, missing))
      case Some(s: TextFieldSchema)     => IO.pure(TextField.sort(s.name, reverse, missing))
      case Some(s: TextListFieldSchema) => IO.pure(TextListField.sort(s.name, reverse, missing))
      case Some(s: GeopointFieldSchema) =>
        by match {
          case _: FieldValueSort =>
            IO.raiseError(UserError(s"to sort by a geopoint, you need to pass lat and lon coordinates"))
          case DistanceSort(field, lat, lon) =>
            IO.pure(GeopointField.sort(field, lat, lon))
        }

      case None if by.field.name == "_score" => IO.pure(new SortField(by.field.name, SortField.Type.SCORE, reverse))
      case None if by.field.name == "_doc"   => IO.pure(new SortField(by.field.name, SortField.Type.DOC, reverse))
      case None =>
        val fieldNames = mapping.fields.keys.map(_.name).toList
        IO.raiseError(
          UserError(s"cannot sort by '${by.field.name}' as it's missing in index schema (fields=$fieldNames)")
        )
    }

  }

}

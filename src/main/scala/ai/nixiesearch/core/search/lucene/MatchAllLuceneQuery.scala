package ai.nixiesearch.core.search.lucene

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.config.mapping.IndexMapping
import cats.effect.IO
import org.apache.lucene.search.{BooleanClause, BooleanQuery, MatchAllDocsQuery, TermQuery, Query as LuceneQuery}

object MatchAllLuceneQuery {
  def create(filter: Option[Filters], mapping: IndexMapping): IO[List[LuceneQuery]] = {
    filter match {
      case Some(value) =>
        value.toLuceneQuery(mapping).flatMap {
          case Some(filterQuery) => IO.pure(List(filterQuery))
          case None              => IO.pure(List(new MatchAllDocsQuery()))
        }
      case None => IO.pure(List(new MatchAllDocsQuery()))
    }
  }
}

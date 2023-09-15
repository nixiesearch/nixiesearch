package ai.nixiesearch.core.search.lucene

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.config.mapping.IndexMapping
import cats.effect.IO
import org.apache.lucene.search.{BooleanClause, BooleanQuery, MatchAllDocsQuery, TermQuery, Query as LuceneQuery}

object MatchAllLuceneQuery {
  def create(filter: Filters, mapping: IndexMapping): IO[List[LuceneQuery]] = filter.toLuceneQuery(mapping).flatMap {
    case None              => IO.pure(List(new MatchAllDocsQuery()))
    case Some(filterQuery) => IO.pure(List(filterQuery))
  }
}

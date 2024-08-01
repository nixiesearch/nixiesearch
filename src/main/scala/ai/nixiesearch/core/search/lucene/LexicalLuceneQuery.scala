package ai.nixiesearch.core.search.lucene

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.config.mapping.{IndexMapping, Language}
import ai.nixiesearch.core.suggest.AnalyzedIterator
import cats.effect.IO
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.{BooleanClause, BooleanQuery, TermQuery, Query as LuceneQuery}

object LexicalLuceneQuery {
  def create(
      field: String,
      query: String,
      filter: Option[Filters],
      analyzer: Language,
      mapping: IndexMapping,
      occur: Occur
  ): IO[List[LuceneQuery]] = {
    filter match {
      case None =>
        IO(List(fieldQuery(field, query, analyzer, occur)))
      case Some(f) =>
        f.toLuceneQuery(mapping).flatMap {
          case Some(filterQuery) =>
            IO {
              val outerQuery = new BooleanQuery.Builder()
              outerQuery.add(new BooleanClause(filterQuery, Occur.FILTER))
              outerQuery.add(new BooleanClause(fieldQuery(field, query, analyzer, occur), Occur.MUST))
              List(outerQuery.build())
            }
          case None =>
            IO {
              List(fieldQuery(field, query, analyzer, occur))
            }
        }
    }
  }

  private def fieldQuery(field: String, query: String, language: Language, occur: Occur): LuceneQuery = {
    val fieldQuery = new BooleanQuery.Builder()
    AnalyzedIterator(language.analyzer, field, query)
      .foreach(term => fieldQuery.add(new BooleanClause(new TermQuery(new Term(field, term)), occur)))
    fieldQuery.build()
  }
}

package ai.nixiesearch.core.suggest

import ai.nixiesearch.core.codec.TextFieldWriter
import cats.effect.IO
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.Term
import org.apache.lucene.search.suggest.document.{
  FuzzyCompletionQuery,
  PrefixCompletionQuery,
  SuggestIndexSearcher,
  TopSuggestDocs
}
import org.apache.lucene.util.automaton.Operations

case class GeneratedSuggestions(field: String, prefix: TopSuggestDocs, fuzzy1: TopSuggestDocs, fuzzy2: TopSuggestDocs)

object GeneratedSuggestions {
  def fromField(
      fieldName: String,
      suggester: SuggestIndexSearcher,
      analyzer: Analyzer,
      query: String,
      count: Int
  ): IO[GeneratedSuggestions] = IO {
    val field = fieldName + TextFieldWriter.SUGGEST_SUFFIX
    GeneratedSuggestions(
      field = fieldName,
      prefix = suggester
        .suggest(
          new PrefixCompletionQuery(analyzer, new Term(field, query)),
          count,
          true
        ),
      fuzzy1 = suggester
        .suggest(
          new FuzzyCompletionQuery(analyzer, new Term(field, query)),
          count,
          true
        ),
      fuzzy2 = suggester
        .suggest(
          new FuzzyCompletionQuery(
            analyzer,
            new Term(field, query),
            null,
            3,
            FuzzyCompletionQuery.DEFAULT_TRANSPOSITIONS,
            FuzzyCompletionQuery.DEFAULT_NON_FUZZY_PREFIX,
            FuzzyCompletionQuery.DEFAULT_MIN_FUZZY_LENGTH,
            FuzzyCompletionQuery.DEFAULT_UNICODE_AWARE,
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
          ),
          count,
          true
        )
    )
  }

}

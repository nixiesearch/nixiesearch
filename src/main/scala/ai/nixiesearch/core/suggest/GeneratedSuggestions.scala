package ai.nixiesearch.core.suggest

import ai.nixiesearch.core.field.TextField
import ai.nixiesearch.core.suggest.GeneratedSuggestions.SuggestDoc
import cats.effect.IO
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.Term
import org.apache.lucene.search.suggest.document.TopSuggestDocs.SuggestScoreDoc
import org.apache.lucene.search.suggest.document.{
  FuzzyCompletionQuery,
  PrefixCompletionQuery,
  RegexCompletionQuery,
  SuggestIndexSearcher,
  TopSuggestDocs
}

import scala.jdk.CollectionConverters.*
import org.apache.lucene.util.automaton.Operations

case class GeneratedSuggestions(
    field: String,
    prefix: List[SuggestDoc],
    fuzzy1: List[SuggestDoc],
    fuzzy2: List[SuggestDoc],
    regex: List[SuggestDoc]
)

object GeneratedSuggestions {
  case class SuggestDoc(doc: Int, suggest: String, score: Float)
  object SuggestDoc {
    def apply(doc: SuggestScoreDoc): SuggestDoc =
      new SuggestDoc(doc.doc, doc.key.toString, doc.score)

    def fromTopSuggestDocs(docs: TopSuggestDocs): List[SuggestDoc] = {
      docs.scoreLookupDocs().toList.map(ssd => SuggestDoc(ssd))
    }
  }

  def fromField(
      fieldName: String,
      suggester: SuggestIndexSearcher,
      analyzer: Analyzer,
      query: String,
      count: Int
  ): IO[GeneratedSuggestions] = IO {
    val field = fieldName + TextField.SUGGEST_SUFFIX
    GeneratedSuggestions(
      field = fieldName,
      prefix = SuggestDoc.fromTopSuggestDocs(
        suggester
          .suggest(
            new PrefixCompletionQuery(analyzer, new Term(field, query)),
            count,
            true
          )
      ),
      fuzzy1 = SuggestDoc.fromTopSuggestDocs(
        suggester
          .suggest(
            new FuzzyCompletionQuery(analyzer, new Term(field, query)),
            count,
            true
          )
      ),
      fuzzy2 = SuggestDoc.fromTopSuggestDocs(
        suggester
          .suggest(
            new FuzzyCompletionQuery(
              analyzer,
              new Term(field, query),
              null,
              2,
              FuzzyCompletionQuery.DEFAULT_TRANSPOSITIONS,
              FuzzyCompletionQuery.DEFAULT_NON_FUZZY_PREFIX,
              FuzzyCompletionQuery.DEFAULT_MIN_FUZZY_LENGTH,
              FuzzyCompletionQuery.DEFAULT_UNICODE_AWARE,
              Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
            ),
            count,
            true
          )
      ),
      regex = SuggestDoc.fromTopSuggestDocs(
        suggester.suggest(new RegexCompletionQuery(new Term(field, s".*$query.*")), count, true)
      )
    )
  }

}

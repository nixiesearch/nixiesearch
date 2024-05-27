package ai.nixiesearch.core.suggest.rank

import ai.nixiesearch.api.SearchRoute.SuggestRequest.DeduplicateOptions
import ai.nixiesearch.api.SearchRoute.SuggestResponse
import ai.nixiesearch.core.suggest.GeneratedSuggestions
import cats.effect.IO

import scala.collection.mutable

object DeduplicateProcessor extends SuggestionProcessor[DeduplicateOptions] {
  override def process(
      candidates: List[GeneratedSuggestions],
      options: DeduplicateOptions
  ): IO[List[SuggestResponse.Suggestion]] = IO {
    val seen = mutable.Set[String]()
    ???
  }
}

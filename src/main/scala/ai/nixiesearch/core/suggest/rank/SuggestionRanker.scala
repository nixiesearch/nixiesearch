package ai.nixiesearch.core.suggest.rank

import ai.nixiesearch.api.SearchRoute.SuggestRequest.SuggestRerankOptions
import ai.nixiesearch.api.SearchRoute.SuggestResponse.Suggestion
import ai.nixiesearch.core.suggest.GeneratedSuggestions
import cats.effect.IO

trait SuggestionRanker[T <: SuggestRerankOptions] {
  def rank(candidates: List[GeneratedSuggestions], options: T): IO[List[Suggestion]]
}

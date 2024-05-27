package ai.nixiesearch.core.suggest.rank

import ai.nixiesearch.api.SearchRoute.SuggestRequest.SuggestRerankOptions
import ai.nixiesearch.api.SearchRoute.SuggestRequest.SuggestRerankOptions.LTROptions
import ai.nixiesearch.api.SearchRoute.SuggestResponse
import ai.nixiesearch.core.suggest.GeneratedSuggestions
import cats.effect.IO

object LTRSuggestionRanker extends SuggestionRanker[LTROptions] {
  override def rank(
      candidates: List[GeneratedSuggestions],
      options: SuggestRerankOptions.LTROptions
  ): IO[List[SuggestResponse.Suggestion]] = IO.raiseError(new NotImplementedError("not yet"))
}

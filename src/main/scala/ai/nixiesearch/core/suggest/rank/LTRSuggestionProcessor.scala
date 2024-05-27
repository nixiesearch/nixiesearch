package ai.nixiesearch.core.suggest.rank

import ai.nixiesearch.api.SearchRoute.SuggestRequest.RerankProcess
import ai.nixiesearch.api.SearchRoute.SuggestRequest.RerankProcess.LTRProcess
import ai.nixiesearch.api.SearchRoute.SuggestResponse
import ai.nixiesearch.core.suggest.GeneratedSuggestions
import cats.effect.IO

object LTRSuggestionProcessor extends SuggestionProcessor[LTRProcess] {
  override def process(
      candidates: List[GeneratedSuggestions],
      options: RerankProcess.LTRProcess
  ): IO[List[SuggestResponse.Suggestion]] = IO.raiseError(new NotImplementedError("not yet"))
}

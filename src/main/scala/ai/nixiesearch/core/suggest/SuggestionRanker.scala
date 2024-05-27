package ai.nixiesearch.core.suggest

import ai.nixiesearch.api.SearchRoute.SuggestRequest
import ai.nixiesearch.api.SearchRoute.SuggestRequest.SuggestRerankOptions.{LTROptions, RRFOptions}
import ai.nixiesearch.api.SearchRoute.SuggestResponse.Suggestion
import ai.nixiesearch.core.suggest.rank.{LTRSuggestionRanker, RRFSuggestionRanker}
import cats.effect.IO

case class SuggestionRanker() {

  // RRF by default
  def rank(candidates: List[GeneratedSuggestions], request: SuggestRequest): IO[List[Suggestion]] = for {
    sorted <- request.rerank match {
      case r: RRFOptions => RRFSuggestionRanker.rank(candidates, r)
      case r: LTROptions => LTRSuggestionRanker.rank(candidates, r)
    }
    top <- IO(sorted.take(request.count))
  } yield {
    top
  }
}

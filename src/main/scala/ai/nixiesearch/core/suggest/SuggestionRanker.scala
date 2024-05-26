package ai.nixiesearch.core.suggest

import ai.nixiesearch.api.SearchRoute.SuggestResponse.Suggestion

case class SuggestionRanker() {
  def rank(candidates: List[GeneratedSuggestions]): List[Suggestion] = {
    candidates.head.prefix.map(doc => Suggestion(doc.suggest, doc.score))
  }
}

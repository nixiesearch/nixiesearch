package ai.nixiesearch.core.suggest

import ai.nixiesearch.api.SearchRoute.SuggestResponse.Suggestion

case class SuggestionRanker() {
  def rank(candidates: List[GeneratedSuggestions]): List[Suggestion] = {
    val br = 1
    candidates.head.prefix.scoreLookupDocs().toList.map(doc => Suggestion(doc.key.toString, doc.score))
  }
}

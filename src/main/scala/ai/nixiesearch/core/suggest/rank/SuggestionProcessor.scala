package ai.nixiesearch.core.suggest.rank

import ai.nixiesearch.api.SearchRoute.SuggestRequest.RerankProcess
import ai.nixiesearch.api.SearchRoute.SuggestResponse.Suggestion
import ai.nixiesearch.core.suggest.GeneratedSuggestions
import ai.nixiesearch.core.suggest.rank.SuggestionProcessor.ProcessorOptions
import cats.effect.IO

trait SuggestionProcessor[T <: ProcessorOptions] {
  def process(candidates: List[GeneratedSuggestions], options: T): IO[List[Suggestion]]
}

object SuggestionProcessor {
  trait ProcessorOptions
}

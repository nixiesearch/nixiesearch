package ai.nixiesearch.core.suggest

import ai.nixiesearch.api.SearchRoute.SuggestRequest
import ai.nixiesearch.api.SearchRoute.SuggestRequest.RerankProcess.{LTRProcess, RRFProcess}
import ai.nixiesearch.api.SearchRoute.SuggestRequest.SuggestProcess
import ai.nixiesearch.api.SearchRoute.SuggestResponse.Suggestion
import ai.nixiesearch.core.suggest.rank.{LTRSuggestionProcessor, RRFSuggestionProcessor}
import cats.effect.IO

case class SuggestionRanker() {

  // RRF by default
  def rank(candidates: List[GeneratedSuggestions], request: SuggestRequest): IO[List[Suggestion]] = for {
    sorted <- request.process.getOrElse(SuggestProcess()).rerank match {
      case Some(r: RRFProcess) => RRFSuggestionProcessor.process(candidates, r)
      case Some(r: LTRProcess) => LTRSuggestionProcessor.process(candidates, r)
      case None                => RRFSuggestionProcessor.process(candidates, RRFProcess())
    }
    top <- IO(sorted.take(request.count))
  } yield {
    top
  }
}

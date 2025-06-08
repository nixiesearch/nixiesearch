package ai.nixiesearch.core.suggest.rank
import ai.nixiesearch.api.SearchRoute.SuggestRequest.SuggestRerankOptions.RRFOptions
import ai.nixiesearch.api.SearchRoute.SuggestResponse
import ai.nixiesearch.api.SearchRoute.SuggestResponse.Suggestion
import io.circe.generic.semiauto.*
import ai.nixiesearch.core.suggest.GeneratedSuggestions
import cats.effect.IO

import scala.collection.mutable

object RRFSuggestionRanker extends SuggestionRanker[RRFOptions] {
  case class Candidate(text: String, score: Float)
  override def rank(candidates: List[GeneratedSuggestions], options: RRFOptions): IO[List[SuggestResponse.Suggestion]] =
    IO {
      val scores = mutable.Map[String, Candidate]()
      for {
        perField            <- candidates
        (suggest, position) <- List.concat(
          perField.prefix.map(_.suggest).zipWithIndex,
          perField.fuzzy1.map(_.suggest).zipWithIndex,
          perField.fuzzy2.map(_.suggest).zipWithIndex,
          perField.regex.map(_.suggest).zipWithIndex
        )
      } {
        val key       = suggest.toLowerCase() // be case insensitive
        val candidate = scores.getOrElse(key, Candidate(suggest, 0.0f))
        scores.addOne(key -> candidate.copy(score = (candidate.score + 1.0f / (options.scale + position))))
      }
      scores.values.toList.map(x => Suggestion(x.text, x.score)).sortBy(-_.score)
    }
}

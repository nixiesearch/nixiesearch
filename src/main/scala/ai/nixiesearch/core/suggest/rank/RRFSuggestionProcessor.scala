package ai.nixiesearch.core.suggest.rank
import ai.nixiesearch.api.SearchRoute.SuggestRequest.RerankProcess.RRFProcess
import ai.nixiesearch.api.SearchRoute.SuggestResponse
import ai.nixiesearch.api.SearchRoute.SuggestResponse.Suggestion
import io.circe.generic.semiauto.*
import ai.nixiesearch.core.suggest.GeneratedSuggestions
import cats.effect.IO
import io.circe.{Decoder, Encoder}

import scala.collection.mutable

object RRFSuggestionProcessor extends SuggestionProcessor[RRFProcess] {

  override def process(candidates: List[GeneratedSuggestions], options: RRFProcess): IO[List[SuggestResponse.Suggestion]] =
    IO {
      val scores = mutable.Map[String, Float]()
      for {
        perField <- candidates
        (suggest, position) <- List.concat(
          perField.prefix.map(_.suggest).zipWithIndex,
          perField.fuzzy1.map(_.suggest).zipWithIndex,
          perField.fuzzy2.map(_.suggest).zipWithIndex,
          perField.regex.map(_.suggest).zipWithIndex
        )
      } {
        val score = scores.getOrElse(suggest, 0.0f)
        scores.addOne(suggest -> (score + 1.0f / (options.scale + position)))
      }
      scores.toList.map(x => Suggestion(x._1, x._2)).sortBy(-_.score)
    }
}

package ai.nixiesearch.core.suggest

import ai.nixiesearch.config.mapping.{Language, SuggestSchema}
import ai.nixiesearch.config.mapping.Language.Generic
import ai.nixiesearch.config.mapping.SuggestSchema.Expand

object SuggestCandidates {
  def fromString(config: SuggestSchema, field: String, source: String): Iterator[String] = {
    val maybeLowercased = config.lowercase match {
      case true  => source.toLowerCase
      case false => source
    }
    config.expand match {
      case Some(Expand(minTerms, maxTerms)) =>
        val tokens = AnalyzedIterator(Generic.analyzer, field, source).toList
        for {
          sliceSize <- (minTerms to math.min(maxTerms, tokens.length)).iterator
          tuple     <- tokens.sliding(sliceSize)
        } yield {
          tuple.mkString(" ")
        }
      case None => Iterator.single(maybeLowercased)
    }
  }
}

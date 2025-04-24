package ai.nixiesearch.core.suggest

import ai.nixiesearch.config.mapping.{Language, SuggestSchema}
import ai.nixiesearch.config.mapping.Language.Generic
import ai.nixiesearch.config.mapping.SuggestSchema.Expand

object SuggestCandidates {
  def fromString(config: SuggestSchema, field: String, source: String): Iterator[String] = {
    config.expand match {
      case Some(Expand(minTerms, maxTerms)) =>
        val tokens = AnalyzedIterator(config.analyze.analyzer, field, source).toList
        for {
          sliceSize <- (minTerms to math.min(maxTerms, tokens.length)).iterator
          tuple     <- tokens.sliding(sliceSize)
        } yield {
          tuple.mkString(" ")
        }
      case None => Iterator.single(source)
    }
  }
}

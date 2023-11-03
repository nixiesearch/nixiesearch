package ai.nixiesearch.core.search.lucene

import ai.nixiesearch.config.mapping.Language
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer
import org.apache.lucene.analysis.{Analyzer, LowerCaseFilter, StopFilter}

case class SuggestionAnalyzer(lowercase: Boolean, stopwords: Boolean, language: Language) extends Analyzer {
  override def createComponents(fieldName: String): Analyzer.TokenStreamComponents = {
    val tokenizer = new ICUTokenizer()
    val next1     = if (lowercase) new LowerCaseFilter(tokenizer) else tokenizer
    val next2     = if (stopwords) new StopFilter(next1, language.stopwords) else next1
    new TokenStreamComponents(tokenizer, next2)
  }
}

package ai.nixiesearch.core.suggest

import org.apache.lucene.analysis.{Analyzer, TokenStream}
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

case class AnalyzedIterator(stream: TokenStream, term: CharTermAttribute) extends Iterator[String] {
  override def hasNext: Boolean = {
    val result = stream.incrementToken()
    if (!result) {
      stream.close()
    }
    result
  }

  override def next(): String = term.toString
}

object AnalyzedIterator {
  def apply(analyzer: Analyzer, field: String, text: String): AnalyzedIterator = {
    val stream = analyzer.tokenStream(field, text)
    stream.reset()
    val term = stream.addAttribute(classOf[CharTermAttribute])
    new AnalyzedIterator(stream, term)
  }
}

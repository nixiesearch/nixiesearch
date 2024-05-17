package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.mapping.Language.English
import ai.nixiesearch.core.suggest.AnalyzedIterator
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LanguageTest extends AnyFlatSpec with Matchers {
  it should "analyze text" in {
    val result = AnalyzedIterator(English.analyzer, "title", "quick brown fox jumps over a lazy dogs")
    result.toList shouldBe List("quick", "brown", "fox", "jump", "over", "lazi", "dog")
  }
}

package ai.nixiesearch.core.suggest

import ai.nixiesearch.config.mapping.Language.{English, Generic}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AnalyzedIteratorTest extends AnyFlatSpec with Matchers {
  it should "tokenize string" in {
    AnalyzedIterator(Generic.analyzer, "none", "hello world").toList shouldBe List("hello", "world")
  }

  it should "not fail on empty strings" in {
    AnalyzedIterator(Generic.analyzer, "none", "").toList shouldBe Nil
  }

  it should "normalize languages" in {
    AnalyzedIterator(English.analyzer, "none", "running shoes").toList shouldBe List("run", "shoe")
  }
}

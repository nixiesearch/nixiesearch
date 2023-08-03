package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.mapping.Language.English
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LanguageTest extends AnyFlatSpec with Matchers {
  it should "analyze text" in {
    val result = English.analyze("quick brown fox jumps over a lazy dogs")
    result shouldBe List("quick", "brown", "fox", "jump", "over", "lazi", "dog")
  }
}

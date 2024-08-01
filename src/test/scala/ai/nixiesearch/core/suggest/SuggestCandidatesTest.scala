package ai.nixiesearch.core.suggest

import ai.nixiesearch.config.mapping.SuggestSchema
import ai.nixiesearch.config.mapping.SuggestSchema.Expand
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SuggestCandidatesTest extends AnyFlatSpec with Matchers {

  it should "generate suggestions with no expand" in {
    val result = SuggestCandidates.fromString(SuggestSchema(expand = None), "title", "hello world").toList
    result shouldBe List("hello world")
  }

  it should "generate suggestions with expand" in {
    val result =
      SuggestCandidates.fromString(SuggestSchema(expand = Some(Expand(1, 3))), "title", "hello world foo bar").toList
    result shouldBe List(
      "hello",
      "world",
      "foo",
      "bar",
      "hello world",
      "world foo",
      "foo bar",
      "hello world foo",
      "world foo bar"
    )
  }

  it should "generate suggestions with expand for short sequences" in {
    val result = SuggestCandidates.fromString(SuggestSchema(expand = Some(Expand(1, 3))), "title", "hello world").toList
    result shouldBe List(
      "hello",
      "world",
      "hello world"
    )
  }
}

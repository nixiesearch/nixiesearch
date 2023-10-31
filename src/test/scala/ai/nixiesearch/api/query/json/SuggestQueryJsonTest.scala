package ai.nixiesearch.api.query.json

import ai.nixiesearch.api.SuggestRoute.Deduplication.NoDedup
import ai.nixiesearch.api.SuggestRoute.SuggestRequest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*

class SuggestQueryJsonTest extends AnyFlatSpec with Matchers {
  it should "decode suggest query with default dedup" in {
    val json = decode[SuggestRequest]("""{"text":"halp"}""")
    json shouldBe Right(SuggestRequest("halp"))
  }
  it should "decode suggest query with no dedup" in {
    val json = decode[SuggestRequest]("""{"text":"halp","deduplication":"false"}""")
    json shouldBe Right(SuggestRequest("halp", deduplication = NoDedup))
  }
}

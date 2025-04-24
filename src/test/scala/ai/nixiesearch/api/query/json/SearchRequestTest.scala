package ai.nixiesearch.api.query.json

import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.query.retrieve.MatchQuery
import ai.nixiesearch.config.mapping.FieldName.StringName
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*

class SearchRequestTest extends AnyFlatSpec with Matchers {
  it should "decode json for match query" in {
    val json    = """{"query": {"match": {"text": "manhattan"}}, "fields": ["a","b"]}"""
    val decoded = decode[SearchRequest](json)
    decoded shouldBe Right(
      SearchRequest(query = MatchQuery("text", "manhattan"), fields = List(StringName("a"), StringName("b")))
    )
  }

}

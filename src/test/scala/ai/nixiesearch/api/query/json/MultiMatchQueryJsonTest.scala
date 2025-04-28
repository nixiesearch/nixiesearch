package ai.nixiesearch.api.query.json

import ai.nixiesearch.api.query.retrieve.MatchQuery.Operator
import ai.nixiesearch.api.query.retrieve.MultiMatchQuery
import ai.nixiesearch.api.query.retrieve.MultiMatchQuery.{BestFieldsQuery, MostFieldsQuery}
import ai.nixiesearch.config.mapping.FieldName.StringName
import io.circe.parser.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MultiMatchQueryJsonTest extends AnyFlatSpec with Matchers {
  it should "decode multi-match default" in {
    val decoded = decode[MultiMatchQuery]("""{"query":"foo","fields":["a","b"]}""")
    decoded shouldBe Right(BestFieldsQuery("foo", List(StringName("a"), StringName("b"))))
  }

  it should "decode multi-match with type" in {
    val decoded = decode[MultiMatchQuery]("""{"query":"foo","fields":["a","b"],"type":"most_fields"}""")
    decoded shouldBe Right(MostFieldsQuery("foo", List(StringName("a"), StringName("b"))))
  }

}

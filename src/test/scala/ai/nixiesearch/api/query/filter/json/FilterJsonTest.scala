package ai.nixiesearch.api.query.filter.json

import ai.nixiesearch.api.filter.Filter
import ai.nixiesearch.api.filter.Predicate.BoolPredicate.AndPredicate
import ai.nixiesearch.api.filter.Predicate.RangePredicate.RangeGteLte
import ai.nixiesearch.api.filter.Predicate.TermPredicate
import io.circe.parser.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FilterJsonTest extends AnyFlatSpec with Matchers {
  it should "decode example" in {
    val json =
      """
        |{
        |        "include": {
        |            "and": [
        |                {"term": {"tag": "red"}},
        |                {"range": {"price": {"gte": 100, "lte": 1000}}}
        |            ]
        |        },
        |        "exclude": {
        |            "term": {"tag": "out-of-stock"}
        |        }
        |    }""".stripMargin
    val decoded = decode[Filter](json)
    decoded shouldBe Right(
      Filter(
        include = Some(AndPredicate(List(TermPredicate("tag", "red"), RangeGteLte("price", 100, 1000)))),
        exclude = Some(TermPredicate("tag", "out-of-stock"))
      )
    )
  }
}

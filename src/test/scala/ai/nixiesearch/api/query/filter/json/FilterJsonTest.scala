package ai.nixiesearch.api.query.filter.json

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.filter.Predicate.BoolPredicate.AndPredicate
import ai.nixiesearch.api.filter.Predicate.{RangePredicate, TermPredicate}
import ai.nixiesearch.core.FiniteRange.Higher.Lte
import ai.nixiesearch.core.FiniteRange.Lower.Gte
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
        |                {"term": {"date": "2024-01-01"}},
        |                {"term": {"datetime": "2024-01-01T00:00:00Z"}},
        |                {"range": {"price": {"gte": 100, "lte": 1000}}}
        |            ]
        |        },
        |        "exclude": {
        |            "term": {"tag": "out-of-stock"}
        |        }
        |    }""".stripMargin
    val decoded = decode[Filters](json)
    decoded shouldBe Right(
      Filters(
        include = Some(
          AndPredicate(
            List(
              TermPredicate("tag", "red"),
              TermPredicate("date", "2024-01-01"),
              TermPredicate("datetime", "2024-01-01T00:00:00Z"),
              RangePredicate("price", Gte(100), Lte(1000))
            )
          )
        ),
        exclude = Some(TermPredicate("tag", "out-of-stock"))
      )
    )
  }
}

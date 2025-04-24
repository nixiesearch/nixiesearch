package ai.nixiesearch.api.query.json

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*

class KnnQueryJsonTest extends AnyFlatSpec with Matchers {
  it should "decode query with vector" in {
    val str     = """{"field": "foo", "query_vector": [1,2,3]}"""
    val decoded = decode[KnnQuery](str)
    decoded should matchPattern {
      case Right(KnnQuery("foo", vector)) if Array.equals(vector, Array(1, 2, 3)) =>
    }
  }
}

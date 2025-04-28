package ai.nixiesearch.api.query.json

import ai.nixiesearch.api.query.retrieve.KnnQuery
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*

import java.util

class KnnQueryJsonTest extends AnyFlatSpec with Matchers {
  it should "decode query with vector" in {
    val str     = """{"field": "foo", "query_vector": [1,2,3]}"""
    val decoded = decode[KnnQuery](str)
    decoded should matchPattern {
      case Right(KnnQuery("foo", vector, _)) if util.Arrays.equals(vector, Array(1f, 2f, 3f)) =>
    }
  }
}

package ai.nixiesearch.api.query.json

import ai.nixiesearch.api.SearchRoute.SearchRequest
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
      case Right(KnnQuery("foo", vector, _, _)) if util.Arrays.equals(vector, Array(1f, 2f, 3f)) =>
    }
  }

  it should "decode full search request with filter" in {
    val str = """{
                |  "query": {
                |    "knn": {
                |      "field": "text",
                |      "query_vector": [
                |        -0.10364249348640442,
                |        0.0293938759714365,
                |        -0.03270823881030083
                |      ],
                |      "num_candidates": 16,
                |      "k": 16
                |    }
                |  },
                |  "size": 16,
                |  "filters": {
                |    "include": {
                |      "term": {
                |        "tag": 90
                |      }
                |    }
                |  }
                |}""".stripMargin
    val decoded = decode[SearchRequest](str)
    decoded.isRight shouldBe true
  }
}

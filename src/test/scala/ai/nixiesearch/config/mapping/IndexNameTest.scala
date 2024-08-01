package ai.nixiesearch.config.mapping

import ai.nixiesearch.config.mapping.IndexNameTest.{IndexNameWrapper, indexNameWrapperDecoder}
import io.circe.Decoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*
import io.circe.generic.semiauto.*

class IndexNameTest extends AnyFlatSpec with Matchers {
  it should "fail decoding broken index names" in {
    val result = decode[IndexNameWrapper]("""{"name": "_admin"}""")
    result shouldBe a[Left[?, ?]]
  }

  it should "parse correct index name" in {
    val result = decode[IndexNameWrapper]("""{"name": "test"}""")
    result shouldBe Right(IndexNameWrapper(IndexName("test")))
  }
}

object IndexNameTest {
  import IndexName.*
  import IndexName.given
  case class IndexNameWrapper(name: IndexName)
  given indexNameWrapperDecoder: Decoder[IndexNameWrapper] = deriveDecoder[IndexNameWrapper]
}

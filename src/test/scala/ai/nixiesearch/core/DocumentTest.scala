package ai.nixiesearch.core

import ai.nixiesearch.core.Field.{IntField, TextField}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*
import io.circe.syntax.*

class DocumentTest extends AnyFlatSpec with Matchers {
  it should "decode flat json documents" in {
    val json = """{"id": "a", "title": "foo", "count": 1}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("id", "a"), TextField("title", "foo"), IntField("count", 1)))
    )
  }

  it should "fail on zero fields" in {
    decode[Document]("{}") shouldBe a[Left[_, _]]
  }

}

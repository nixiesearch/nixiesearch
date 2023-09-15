package ai.nixiesearch.core

import ai.nixiesearch.core.Field.{IntField, TextField}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*
import io.circe.syntax.*

class DocumentJsonTest extends AnyFlatSpec with Matchers {
  it should "decode flat json documents" in {
    val json = """{"_id": "a", "title": "foo", "count": 1}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), IntField("count", 1)))
    )
  }

  it should "fail on zero fields" in {
    decode[Document]("{}") shouldBe a[Left[_, _]]
  }

  it should "generate synthetic ID" in {
    decode[Document]("""{"title":"foo"}""") shouldBe a[Right[_, _]]
  }

  it should "accept numeric ids" in {
    decode[Document]("""{"_id": 1,"title":"foo"}""") shouldBe Right(
      Document(List(TextField("_id", "1"), TextField("title", "foo")))
    )
  }

  it should "fail on real ids" in {
    decode[Document]("""{"_id": 1.666,"title":"foo"}""") shouldBe a[Left[_, _]]

  }

}

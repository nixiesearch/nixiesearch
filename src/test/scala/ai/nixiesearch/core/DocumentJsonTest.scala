package ai.nixiesearch.core

import ai.nixiesearch.core.Field.{FloatField, IntField, TextField, TextListField}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*
import io.circe.syntax.*

class DocumentJsonTest extends AnyFlatSpec with Matchers {
  it should "decode flat json documents" in {
    val json = """{"_id": "a", "title": "foo", "count": 1}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), FloatField("count", 1)))
    )
  }

  it should "decode 2x nested json documents" in {
    val json = """{"_id": "a", "title": "foo", "info": {"group": 1}}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), FloatField("info.group", 1)))
    )
  }

  it should "decode 3x nested json documents" in {
    val json = """{"_id": "a", "title": "foo", "info": {"group": {"deep":1}}}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), FloatField("info.group.deep", 1)))
    )
  }

  it should "decode arrays of nested json documents" in {
    val json = """{"_id": "a", "title": "foo", "tracks": [{"name": "foo"}]}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), TextListField("tracks.name", List("foo"))))
    )
  }

  it should "decode arrays of strings" in {
    val json = """{"_id": "a", "title": "foo", "tracks": ["foo"]}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), TextListField("tracks", List("foo"))))
    )
  }

  it should "skip empty arrays" in {
    val json = """{"_id": "a", "title": "foo", "tracks": []}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo")))
    )
  }
  it should "skip arrays of nulls" in {
    val json = """{"_id": "a", "title": "foo", "tracks": [null]}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo")))
    )
  }

  it should "skip empty objects" in {
    val json = """{"_id": "a", "title": "foo", "tracks": {}}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo")))
    )
  }

  it should "accept null values" in {
    val json = """{"_id": "a", "title": null, "count": 1}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), FloatField("count", 1)))
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

package ai.nixiesearch.core

import ai.nixiesearch.config.FieldSchema.{
  BooleanFieldSchema,
  DoubleFieldSchema,
  FloatFieldSchema,
  IntFieldSchema,
  LongFieldSchema,
  TextFieldSchema,
  TextListFieldSchema
}
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{IndexMapping, IndexName}
import ai.nixiesearch.core.Field.{BooleanField, DoubleField, FloatField, IntField, LongField, TextField, TextListField}
import ai.nixiesearch.util.TestIndexMapping
import io.circe.Decoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*

class DocumentJsonTest extends AnyFlatSpec with Matchers {
  it should "decode flat json documents" in {
    given decoder: Decoder[Document] = Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema("title"))))
    val json                         = """{"_id": "a", "title": "foo", "count": 1}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), FloatField("count", 1)))
    )
  }

  it should "decode 2x nested json documents" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema("title"), TextFieldSchema("info.group"))))
    val json = """{"_id": "a", "title": "foo", "info": {"group": 1}}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), FloatField("info.group", 1)))
    )
  }

  it should "decode 3x nested json documents" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema("title"), TextFieldSchema("info.group.deep"))))
    val json = """{"_id": "a", "title": "foo", "info": {"group": {"deep":1}}}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), FloatField("info.group.deep", 1)))
    )
  }

  it should "decode arrays of nested json documents" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema("title"), TextListFieldSchema("tracks.name"))))
    val json = """{"_id": "a", "title": "foo", "tracks": [{"name": "foo"}]}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), TextListField("tracks.name", List("foo"))))
    )
  }

  it should "decode nested arrays of nested json documents" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema("title"), TextListFieldSchema("tracks.name"))))
    val json = """{"_id": "a", "title": "foo", "tracks": [{"name": ["foo"]}]}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), TextListField("tracks.name", List("foo"))))
    )
  }

  it should "decode arrays of strings" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema("title"), TextListFieldSchema("tracks"))))
    val json = """{"_id": "a", "title": "foo", "tracks": ["foo"]}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), TextListField("tracks", List("foo"))))
    )
  }

  it should "skip empty arrays" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema("title"), TextListFieldSchema("tracks"))))
    val json = """{"_id": "a", "title": "foo", "tracks": []}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo")))
    )
  }
  it should "skip arrays of nulls" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema("title"), TextListFieldSchema("tracks"))))
    val json = """{"_id": "a", "title": "foo", "tracks": [null]}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo")))
    )
  }

  it should "skip empty objects" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema("title"))))
    val json = """{"_id": "a", "title": "foo", "tracks": {}}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo")))
    )
  }

  it should "accept null values" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema("title"), IntFieldSchema("count"))))
    val json = """{"_id": "a", "title": null, "count": 1}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), FloatField("count", 1)))
    )
  }

  it should "fail on zero fields" in {
    given decoder: Decoder[Document] = Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema("title"))))
    decode[Document]("{}") shouldBe a[Left[?, ?]]
  }

  it should "generate synthetic ID" in {
    given decoder: Decoder[Document] = Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema("title"))))
    decode[Document]("""{"title":"foo"}""") shouldBe a[Right[?, ?]]
  }

  it should "accept numeric ids" in {
    given decoder: Decoder[Document] = Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema("title"))))
    decode[Document]("""{"_id": 1,"title":"foo"}""") shouldBe Right(
      Document(List(TextField("_id", "1"), TextField("title", "foo")))
    )
  }

  it should "fail on real ids" in {
    given decoder: Decoder[Document] = Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema("title"))))
    decode[Document]("""{"_id": 1.666,"title":"foo"}""") shouldBe a[Left[?, ?]]
  }

  it should "fail on bool ids" in {
    given decoder: Decoder[Document] = Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema("title"))))
    decode[Document]("""{"_id": true,"title":"foo"}""") shouldBe a[Left[?, ?]]
  }

  it should "fail on bool arrays" in {
    given decoder: Decoder[Document] = Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema("title"))))
    decode[Document]("""{"_id": 1,"title":[true, false]}""") shouldBe a[Left[?, ?]]
  }

  it should "decode booleans" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema("title"), BooleanFieldSchema("flag"))))
    val json = """{"_id": "a", "title": "foo", "flag": true}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), BooleanField("flag", true)))
    )
  }

  it should "cast numeric fields to schema" in {
    val json = """{"_id": "a","count1": 1,"count2": 1,"count3": 1,"count4": 1}"""
    val mapping = IndexMapping(
      name = IndexName("test"),
      fields = List(
        TextFieldSchema("_id"),
        IntFieldSchema("count1"),
        FloatFieldSchema("count2"),
        LongFieldSchema("count3"),
        DoubleFieldSchema("count4")
      ),
      store = LocalStoreConfig(MemoryLocation())
    )

    given decoder: Decoder[Document] = Document.decoderFor(mapping)

    decode[Document](json) shouldBe Right(
      Document(
        List(
          TextField("_id", "a"),
          IntField("count1", 1),
          FloatField("count2", 1),
          LongField("count3", 1),
          DoubleField("count4", 1)
        )
      )
    )
  }

}

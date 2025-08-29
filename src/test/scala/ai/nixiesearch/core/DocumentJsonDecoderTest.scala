package ai.nixiesearch.core

import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.{FieldName, IndexMapping, IndexName, SearchParams}
import ai.nixiesearch.core.field.*
import ai.nixiesearch.util.TestIndexMapping
import io.circe.{Decoder, Json}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.parser.*
import ai.nixiesearch.config.mapping.FieldName.{StringName, WildcardName}
import ai.nixiesearch.config.mapping.SearchParams.{SemanticInferenceParams, SemanticParams}
import ai.nixiesearch.core.Document.JsonScalar.{JNumber, JString, JStringArray}
import ai.nixiesearch.core.nn.ModelRef

class DocumentJsonDecoderTest extends AnyFlatSpec with Matchers {
  it should "decode flat json documents" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title")),
            IntFieldSchema(StringName("count"))
          )
        )
      )
    val json = """{"_id": "a", "title": "foo", "count": 1}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), IntField("count", 1)))
    )
  }

  it should "decode flat docs with wildcarts" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(FieldName.parse("field_*_str").toOption.get),
            IntFieldSchema(FieldName.parse("field_*_int").toOption.get)
          )
        )
      )
    val json = """{"_id": "a", "field_title_str": "foo", "field_count_int": 1}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("field_title_str", "foo"), IntField("field_count_int", 1)))
    )
  }

  it should "decode nested docs with wildcarts" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(FieldName.parse("root.field_*_str").toOption.get),
            IntFieldSchema(FieldName.parse("root.field_*_int").toOption.get)
          )
        )
      )
    val json = """{"_id": "a", "root": {"field_title_str": "foo", "field_count_int": 1}}"""
    decode[Document](json) shouldBe Right(
      Document(
        List(TextField("_id", "a"), TextField("root.field_title_str", "foo"), IntField("root.field_count_int", 1))
      )
    )
  }

  it should "decode nested array docs with wildcarts" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextListFieldSchema(FieldName.parse("root.field_*_str").toOption.get)
          )
        )
      )
    val json = """{"_id": "a", "root": [{"field_title_str": "foo"}]}"""
    decode[Document](json) shouldBe Right(
      Document(
        List(TextField("_id", "a"), TextListField("root.field_title_str", List("foo")))
      )
    )
  }

  it should "decode 2x nested json documents" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title")),
            FloatFieldSchema(StringName("info.group"))
          )
        )
      )
    val json = """{"_id": "a", "title": "foo", "info": {"group": 1}}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), FloatField("info.group", 1)))
    )
  }

  it should "decode 3x nested json documents" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title")),
            FloatFieldSchema(StringName("info.group.deep"))
          )
        )
      )
    val json = """{"_id": "a", "title": "foo", "info": {"group": {"deep":1}}}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), FloatField("info.group.deep", 1)))
    )
  }

  it should "decode arrays of nested json documents" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title")),
            TextListFieldSchema(StringName("tracks.name"))
          )
        )
      )
    val json = """{"_id": "a", "title": "foo", "tracks": [{"name": "foo"}]}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), TextListField("tracks.name", List("foo"))))
    )
  }

  it should "decode nested arrays of nested json documents" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title")),
            TextListFieldSchema(StringName("tracks.name"))
          )
        )
      )
    val json = """{"_id": "a", "title": "foo", "tracks": [{"name": ["foo"]}]}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), TextListField("tracks.name", List("foo"))))
    )
  }

  it should "decode arrays of strings" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title")),
            TextListFieldSchema(StringName("tracks"))
          )
        )
      )
    val json = """{"_id": "a", "title": "foo", "tracks": ["foo"]}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), TextListField("tracks", List("foo"))))
    )
  }

  it should "decode arrays of ints" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title")),
            IntListFieldSchema(StringName("lengths"))
          )
        )
      )

    val json = """{"_id": "a", "title": "foo", "lengths": [1,2,3]}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), IntListField("lengths", List(1, 2, 3))))
    )
  }

  it should "decode arrays of longs" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title")),
            LongListFieldSchema(StringName("lengths"))
          )
        )
      )

    val json = """{"_id": "a", "title": "foo", "lengths": [1,2,3]}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), LongListField("lengths", List(1L, 2L, 3L))))
    )
  }

  it should "decode arrays of doubles" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title")),
            DoubleListFieldSchema(StringName("lengths"))
          )
        )
      )

    val json = """{"_id": "a", "title": "foo", "lengths": [1.0,2.0,3.0]}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), DoubleListField("lengths", List(1.0, 2.0, 3.0))))
    )
  }

  it should "decode arrays of floats" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title")),
            FloatListFieldSchema(StringName("lengths"))
          )
        )
      )

    val json = """{"_id": "a", "title": "foo", "lengths": [1.0,2.0,3.0]}"""
    decode[Document](json) shouldBe Right(
      Document(
        List(TextField("_id", "a"), TextField("title", "foo"), FloatListField("lengths", List(1.0f, 2.0f, 3.0f)))
      )
    )
  }

  it should "skip empty arrays" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title")),
            TextListFieldSchema(StringName("tracks"))
          )
        )
      )
    val json = """{"_id": "a", "title": "foo", "tracks": []}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo")))
    )
  }

  it should "skip empty arrays not defined in schema" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title"))
          )
        )
      )
    val json = """{"_id": "a", "title": "foo", "tracks": []}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo")))
    )
  }

  it should "fail on arrays of nulls" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping("test", List(TextFieldSchema(StringName("title")), TextListFieldSchema(StringName("tracks"))))
      )
    val json = """{"_id": "a", "title": "foo", "tracks": [null]}"""
    decode[Document](json) shouldBe a[Left[?, ?]]
  }

  it should "fail on nested ints" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping("test", List(TextFieldSchema(StringName("title")), TextListFieldSchema(StringName("tracks"))))
      )
    val json = """{"_id": "a", "title": "foo", "tracks": [1,2,3]}"""
    decode[Document](json) shouldBe a[Left[?, ?]]
  }

  it should "skip empty objects" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping("test", List(TextFieldSchema(StringName("_id")), TextFieldSchema(StringName("title"))))
      )
    val json = """{"_id": "a", "title": "foo", "tracks": {}}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo")))
    )
  }

  it should "not accept null values" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title")),
            IntFieldSchema(StringName("count"))
          )
        )
      )
    val json    = """{"_id": "a", "title": null, "count": 1}"""
    val decoded = decode[Document](json)
    decoded shouldBe a[Left[?, ?]]
  }

  it should "fail on zero fields" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(TestIndexMapping("test", List(TextFieldSchema(StringName("title")))))
    decode[Document]("{}") shouldBe a[Left[?, ?]]
  }

  it should "generate synthetic ID" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping("test", List(TextFieldSchema(StringName("_id")), TextFieldSchema(StringName("title"))))
      )
    val ids = decode[Document]("""{"title":"foo"}""").map(_.fields.collectFirst { case TextField("_id", value, _) =>
      value
    })
    ids should matchPattern { case Right(Some(_)) =>
    }
  }

  it should "accept numeric ids" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping("test", List(TextFieldSchema(StringName("_id")), TextFieldSchema(StringName("title"))))
      )
    decode[Document]("""{"_id": 1,"title":"foo"}""") shouldBe Right(
      Document(List(TextField("_id", "1"), TextField("title", "foo")))
    )
  }

  it should "fail on real ids" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping("test", List(TextFieldSchema(StringName("_id")), TextFieldSchema(StringName("title"))))
      )
    decode[Document]("""{"_id": 1.666,"title":"foo"}""") shouldBe a[Left[?, ?]]
  }

  it should "fail on bool ids" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping("test", List(TextFieldSchema(StringName("_id")), TextFieldSchema(StringName("title"))))
      )
    decode[Document]("""{"_id": true,"title":"foo"}""") shouldBe a[Left[?, ?]]
  }

  it should "fail on bool arrays" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping("test", List(TextFieldSchema(StringName("_id")), TextFieldSchema(StringName("title"))))
      )
    decode[Document]("""{"_id": 1,"title":[true, false]}""") shouldBe a[Left[?, ?]]
  }

  it should "decode booleans" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title")),
            BooleanFieldSchema(StringName("flag"))
          )
        )
      )
    val json = """{"_id": "a", "title": "foo", "flag": true}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), BooleanField("flag", true)))
    )
  }

  it should "decode geopoints" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            GeopointFieldSchema(StringName("point1")),
            GeopointFieldSchema(StringName("point2"))
          )
        )
      )
    val json = """{"_id": "a", "point1": {"lat": 1, "lon": 2}, "point2": {"lon": 1, "lat": 2}}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), GeopointField("point1", 1, 2), GeopointField("point2", 2, 1)))
    )
  }

  it should "handle broken geopoints" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            GeopointFieldSchema(StringName("point1")),
            GeopointFieldSchema(StringName("point2"))
          )
        )
      )
    val json = """{"_id": "a", "point1": {"lon": 2}, "point2": {"lon": 1, "salat": 2}}"""
    decode[Document](json) shouldBe a[Left[?, ?]]
  }

  it should "decode dates" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title")),
            DateFieldSchema(StringName("date"))
          )
        )
      )
    val json = """{"_id": "a", "title": "foo", "date": "2025-01-01"}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), DateField("date", 20089)))
    )
  }

  it should "decode datetime" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title")),
            DateTimeFieldSchema(StringName("datetime"))
          )
        )
      )
    val json = """{"_id": "a", "title": "foo", "datetime": "1970-01-01T00:00:01Z"}"""
    decode[Document](json) shouldBe Right(
      Document(List(TextField("_id", "a"), TextField("title", "foo"), DateTimeField("datetime", 1000)))
    )
  }

  it should "decode pre-embedded text fields" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(
              StringName("title"),
              search = SearchParams(semantic = Some(SemanticInferenceParams(model = ModelRef("text"))))
            )
          )
        )
      )
    val json = """{"_id": "a", "title": {"text": "foo", "embedding": [1,2,3]}}"""
    decode[Document](json) should matchPattern {
      case Right(
            Document(List(TextField("_id", "a", _), TextField("title", "foo", Some(_))))
          ) => // ok
    }
  }

  it should "reject documents with missing required fields" in {
    given decoder: Decoder[Document] =
      Document.decoderFor(
        TestIndexMapping(
          "test",
          List(
            TextFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title"), required = true),
            IntFieldSchema(StringName("count"))
          )
        )
      )
    val json = """{"_id": "a", "count": 1}"""
    decode[Document](json) shouldBe a[Left[?, ?]]
  }

}

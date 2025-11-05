package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.config.StoreConfig.LocalStoreConfig
import ai.nixiesearch.config.StoreConfig.LocalStoreLocation.MemoryLocation
import ai.nixiesearch.config.mapping.FieldName.{NestedName, StringName, WildcardName}
import ai.nixiesearch.config.mapping.SearchParams.{SemanticInferenceParams, SemanticParams}
import ai.nixiesearch.config.mapping.{FieldName, IndexMapping, IndexName, SearchParams}
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.{Document, DocumentDecoder}
import ai.nixiesearch.util.TestIndexMapping
import com.github.plokhotnyuk.jsoniter_scala.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DocumentNestedJsonTest extends AnyFlatSpec with Matchers {

  def decode[T](json: String)(using codec: JsonValueCodec[T]): Either[Throwable, T] = {
    try {
      Right(readFromString(json))
    } catch {
      case e: Throwable => Left(e)
    }
  }

  it should "decode nested docs with wildcards" in {
    given codec: JsonValueCodec[Document] =
      DocumentDecoder.codec(
        TestIndexMapping(
          "test",
          List(
            IdFieldSchema(StringName("_id")),
            TextFieldSchema(FieldName.parse("root.field_*_str").toOption.get),
            IntFieldSchema(FieldName.parse("root.field_*_int").toOption.get)
          )
        )
      )
    val json = """{"_id": "a", "root": {"field_title_str": "foo", "field_count_int": 1}}"""
    decode[Document](json) shouldBe Right(
      Document(
        List(IdField("_id", "a"), TextField("root.field_title_str", "foo"), IntField("root.field_count_int", 1))
      )
    )
  }

  it should "decode nested array docs with wildcards" in {
    given codec: JsonValueCodec[Document] =
      DocumentDecoder.codec(
        TestIndexMapping(
          "test",
          List(
            IdFieldSchema(StringName("_id")),
            TextListFieldSchema(FieldName.parse("root.field_*_str").toOption.get)
          )
        )
      )
    val json = """{"_id": "a", "root": [{"field_title_str": "foo"}]}"""
    decode[Document](json) shouldBe Right(
      Document(
        List(IdField("_id", "a"), TextListField("root.field_title_str", List("foo")))
      )
    )
  }

  it should "decode 2x nested json documents" in {
    given codec: JsonValueCodec[Document] =
      DocumentDecoder.codec(
        TestIndexMapping(
          "test",
          List(
            IdFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title")),
            FloatFieldSchema(NestedName("info.group", "info", "group"))
          )
        )
      )
    val json = """{"_id": "a", "title": "foo", "info": {"group": 1}}"""
    decode[Document](json) shouldBe Right(
      Document(List(IdField("_id", "a"), TextField("title", "foo"), FloatField("info.group", 1)))
    )
  }

  it should "decode arrays of nested json documents" in {
    given codec: JsonValueCodec[Document] =
      DocumentDecoder.codec(
        TestIndexMapping(
          "test",
          List(
            IdFieldSchema(StringName("_id")),
            TextFieldSchema(StringName("title")),
            TextListFieldSchema(StringName("tracks.name"))
          )
        )
      )
    val json = """{"_id": "a", "title": "foo", "tracks": [{"name": "foo"}]}"""
    decode[Document](json) shouldBe Right(
      Document(List(IdField("_id", "a"), TextField("title", "foo"), TextListField("tracks.name", List("foo"))))
    )
  }

}

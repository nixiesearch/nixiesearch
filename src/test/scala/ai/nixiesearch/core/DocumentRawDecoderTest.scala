package ai.nixiesearch.core

import ai.nixiesearch.config.FieldSchema.{IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.field.{IntField, TextField}
import ai.nixiesearch.util.TestIndexMapping
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.github.plokhotnyuk.jsoniter_scala.core.*

class DocumentRawDecoderTest extends AnyFlatSpec with Matchers {

  it should "decode strings" in {
    val mapping = TestIndexMapping("test", List(TextFieldSchema(StringName("name"))))
    val result = readFromString("""{"name": "value"}""")(using DocumentDecoder.codec(mapping))
    result shouldBe Document(TextField("name", "value"))
  }

  it should "decode ints" in {
    val mapping = TestIndexMapping("test", List(IntFieldSchema(StringName("name"))))
    val result = readFromString("""{"name": 1}""")(using DocumentDecoder.codec(mapping))
    result shouldBe Document(IntField("name", 1))
  }

  it should "decode ints but fail on floats" in {
    val mapping = TestIndexMapping("test", List(IntFieldSchema(StringName("name"))))
    val result = readFromString("""{"name": 1.5}""")(using DocumentDecoder.codec(mapping) )
    result shouldBe Document(IntField("name", 1))
  }
}

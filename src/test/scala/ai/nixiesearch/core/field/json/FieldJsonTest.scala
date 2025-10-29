package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.core.{DocumentDecoder, Field}
import ai.nixiesearch.util.TestIndexMapping
import com.github.plokhotnyuk.jsoniter_scala.core.readFromString

trait FieldJsonTest {
  def decode(field: FieldSchema[?], json: String): Field = {
    val result = readFromString(json)(using DocumentDecoder.codec(TestIndexMapping("t", List(field))))
    result.fields.head
  }

}

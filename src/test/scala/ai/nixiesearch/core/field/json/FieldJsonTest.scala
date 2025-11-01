package ai.nixiesearch.core.field.json

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.core.{DocumentDecoder, Field}
import ai.nixiesearch.util.TestIndexMapping
import com.github.plokhotnyuk.jsoniter_scala.core.readFromString

trait FieldJsonTest {
  def decode[T <: Field, S <: FieldSchema[T]](field: S, json: String): Option[T] = {
    val result = readFromString(json)(using DocumentDecoder.codec(TestIndexMapping("t", List(field))))
    result.fields.headOption.map(_.asInstanceOf[T])
  }

}

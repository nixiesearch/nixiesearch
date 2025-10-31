package ai.nixiesearch.api.query.sort

import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Field.TextField

class TextSortTest extends SortSuite[TextField, TextFieldSchema, String] {
  override def field(name: FieldName, value: String): TextField = TextField(name.name, value)
  override def schema(name: FieldName): TextFieldSchema         = TextFieldSchema(name, sort = true)
  override def values: List[String]                             = List(1, 2, 3, 4, 5).map(_.toString)
}

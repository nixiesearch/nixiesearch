package ai.nixiesearch.api.query.sort

import ai.nixiesearch.config.FieldSchema.TextListFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.field.TextListField

class TextListSortTest extends SortSuite[TextListField, TextListFieldSchema, String] {
  override def field(name: FieldName, value: String): TextListField = TextListField(name.name, List(value))
  override def schema(name: FieldName): TextListFieldSchema         = TextListFieldSchema(name, sort = true)
  override def values: List[String]                                 = List(1, 2, 3, 4, 5).map(_.toString)
}

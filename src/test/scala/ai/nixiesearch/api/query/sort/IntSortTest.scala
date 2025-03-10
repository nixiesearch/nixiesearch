package ai.nixiesearch.api.query.sort

import ai.nixiesearch.config.FieldSchema.IntFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.field.IntField

class IntSortTest extends SortSuite[IntField, IntFieldSchema, Int] {
  override def field(name: FieldName, value: Int): IntField = IntField(name.name, value)
  override def schema(name: FieldName): IntFieldSchema      = IntFieldSchema(name, sort = true, facet = true)
  override def values: List[Int]                            = List(1, 2, 3, 4, 5)
}

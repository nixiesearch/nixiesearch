package ai.nixiesearch.api.query.sort

import ai.nixiesearch.config.FieldSchema.LongFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Field.LongField

class LongSortTest extends SortSuite[LongField, LongFieldSchema, Long] {
  override def field(name: FieldName, value: Long): LongField = LongField(name.name, value)
  override def schema(name: FieldName): LongFieldSchema       = LongFieldSchema(name, sort = true)
  override def values: List[Long]                             = List(1L, 2L, 3L, 4L, 5L)
}

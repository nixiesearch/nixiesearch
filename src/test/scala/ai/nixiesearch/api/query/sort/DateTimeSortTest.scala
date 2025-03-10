package ai.nixiesearch.api.query.sort

import ai.nixiesearch.config.FieldSchema.DateTimeFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.field.DateTimeField

class DateTimeSortTest extends SortSuite[DateTimeField, DateTimeFieldSchema, Long] {
  override def field(name: FieldName, value: Long): DateTimeField = DateTimeField(name.name, value)
  override def schema(name: FieldName): DateTimeFieldSchema       = DateTimeFieldSchema(name, sort = true)
  override def values: List[Long]                                 = List(1, 2, 3, 4, 5)
}

package ai.nixiesearch.api.query.sort

import ai.nixiesearch.config.FieldSchema.DateFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.field.DateField

class DateFieldSort extends SortSuite[DateField, DateFieldSchema, Int] {
  override def field(name: FieldName, value: Int): DateField = DateField(name.name, value)
  override def schema(name: FieldName): DateFieldSchema      = DateFieldSchema(name, sort = true)
  override def values: List[Int]                             = List(1, 2, 3, 4, 5)
}

package ai.nixiesearch.api.query.sort

import ai.nixiesearch.config.FieldSchema.FloatFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.field.FloatField

class FloatSortTest extends SortSuite[FloatField, FloatFieldSchema, Float] {
  override def field(name: FieldName, value: Float): FloatField = FloatField(name.name, value)
  override def schema(name: FieldName): FloatFieldSchema        = FloatFieldSchema(name, sort = true)
  override def values: List[Float]                              = List(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
}

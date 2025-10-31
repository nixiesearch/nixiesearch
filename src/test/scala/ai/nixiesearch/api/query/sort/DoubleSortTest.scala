package ai.nixiesearch.api.query.sort

import ai.nixiesearch.config.FieldSchema.DoubleFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Field.DoubleField

class DoubleSortTest extends SortSuite[DoubleField, DoubleFieldSchema, Double] {
  override def field(name: FieldName, value: Double): DoubleField = DoubleField(name.name, value)
  override def schema(name: FieldName): DoubleFieldSchema         = DoubleFieldSchema(name, sort = true)
  override def values: List[Double]                               = List(1.0, 2.0, 3.0, 4.0, 5.0)
}

package ai.nixiesearch.api.query.sort

import ai.nixiesearch.config.FieldSchema.BooleanFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Field.BooleanField

class BooleanSortTest extends SortSuite[BooleanField, BooleanFieldSchema, Boolean] {
  override def field(name: FieldName, value: Boolean): BooleanField = BooleanField(name.name, value)
  override def schema(name: FieldName): BooleanFieldSchema          = BooleanFieldSchema(name, sort = true)
  override def values: List[Boolean]                                = List(false, true)
}

package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema.IntFieldSchema
import ai.nixiesearch.core.Field.IntField
import org.apache.lucene.document.{NumericDocValuesField, StoredField}
import org.apache.lucene.index.IndexableField

import java.util

// todo for int[]: use SortedNumericSortField for sorting
case class IntFieldWriter() extends FieldWriter[IntField, IntFieldSchema] {
  override def write(field: IntField, spec: IntFieldSchema, buffer: util.ArrayList[IndexableField]): Unit = {
    if (spec.facet || spec.filter || spec.sort) {
      buffer.add(new NumericDocValuesField(field.name, field.value))
    }
    if (spec.store) {
      buffer.add(new StoredField(field.name, field.value))
    }
  }
}

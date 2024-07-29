package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema.BooleanFieldSchema
import ai.nixiesearch.core.Field.BooleanField
import org.apache.lucene.document.{
  NumericDocValuesField,
  SortedNumericDocValuesField,
  StoredField,
  Document as LuceneDocument
}
import org.apache.lucene.index.IndexableField
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.Document as LuceneDocument

class BooleanFieldWriter extends FieldWriter[BooleanField, BooleanFieldSchema] {
  override def write(
      field: BooleanField,
      spec: BooleanFieldSchema,
      buffer: LuceneDocument,
      embeddings: Map[String, Array[Float]] = Map.empty
  ): Unit = {
    if (spec.filter || spec.sort) {
      buffer.add(new org.apache.lucene.document.IntField(field.name, field.intValue, Store.NO))
    }
    if (spec.facet) {
      buffer.add(new SortedNumericDocValuesField(field.name, field.intValue))
    }
    if (spec.store) {
      buffer.add(new StoredField(field.name, field.intValue))
    }
  }

}

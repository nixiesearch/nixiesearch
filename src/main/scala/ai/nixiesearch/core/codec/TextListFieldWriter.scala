package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema.TextListFieldSchema
import ai.nixiesearch.core.Field.TextListField
import org.apache.lucene.index.IndexableField
import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.SearchType
import ai.nixiesearch.core.Field.*
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{BinaryDocValuesField, SortedSetDocValuesField, StoredField, StringField}
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField
import org.apache.lucene.index.IndexableField
import org.apache.lucene.util.BytesRef
import org.apache.lucene.document.{Document => LuceneDocument}
import java.util

case class TextListFieldWriter() extends FieldWriter[TextListField, TextListFieldSchema] {
  import TextFieldWriter._
  override def write(field: TextListField, spec: TextListFieldSchema, buffer: LuceneDocument): Unit = {
    val search = spec.search match {
      case SearchType.LexicalSearch(_) => true
      case _                           => false
    }
    field.value.foreach(item => {
      if (spec.store) {
        buffer.add(new StoredField(field.name, item))
      }
      if (spec.facet) {
        val trimmed = if (item.length > MAX_FACET_SIZE) item.substring(0, MAX_FACET_SIZE) else item
        buffer.add(new SortedSetDocValuesFacetField(field.name, trimmed))
      }
      if (spec.filter || spec.facet) {
        buffer.add(new StringField(field.name + RAW_SUFFIX, item, Store.NO))
      }
      if (spec.sort) {
        buffer.add(new SortedSetDocValuesField(field.name, new BytesRef(item)))
      }
      if (search) {
        val trimmed = if (item.length > MAX_FIELD_SEARCH_SIZE) item.substring(0, MAX_FIELD_SEARCH_SIZE) else item
        buffer.add(new org.apache.lucene.document.TextField(field.name, trimmed, Store.NO))
      }
    })
  }
}

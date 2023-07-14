package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.SearchType
import ai.nixiesearch.core.Field.*
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{BinaryDocValuesField, StoredField, StringField}
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField
import org.apache.lucene.index.IndexableField
import org.apache.lucene.util.BytesRef

import java.util

case class TextFieldWriter() extends FieldWriter[TextField, TextFieldSchema] {
  import TextFieldWriter._
  override def write(field: TextField, spec: TextFieldSchema, buffer: util.ArrayList[IndexableField]): Unit = {
    if (spec.store) {
      buffer.add(new StoredField(field.name, field.value))
    }
    if (spec.facet) {
      val trimmed = if (field.value.length > MAX_FACET_SIZE) field.value.substring(0, MAX_FACET_SIZE) else field.value
      buffer.add(new SortedSetDocValuesFacetField(field.name, trimmed))
    }
    if (spec.filter || spec.facet) {
      buffer.add(new StringField(field.name + RAW_SUFFIX, field.value, Store.NO))
    }
    if (spec.sort) {
      buffer.add(new BinaryDocValuesField(field.name, new BytesRef(field.value)))
    }
    spec.search match {
      case SearchType.LexicalSearch(_) =>
        val trimmed =
          if (field.value.length > MAX_FIELD_SEARCH_SIZE) field.value.substring(0, MAX_FIELD_SEARCH_SIZE)
          else field.value
        buffer.add(new org.apache.lucene.document.TextField(field.name, trimmed, Store.NO))
      case _ => // nope
    }
  }
}

object TextFieldWriter {
  val MAX_FACET_SIZE        = 1024
  val MAX_FIELD_SEARCH_SIZE = 32000
  val RAW_SUFFIX            = "_raw"
}

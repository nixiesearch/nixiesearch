package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema.TextListFieldSchema
import ai.nixiesearch.config.mapping.SearchType
import ai.nixiesearch.config.mapping.SearchType.{HybridSearch, LexicalSearch}
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.suggest.SuggestCandidates
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{SortedSetDocValuesField, StoredField, StringField, Document as LuceneDocument}
import org.apache.lucene.util.BytesRef
import org.apache.lucene.search.suggest.document.SuggestField

case class TextListFieldWriter() extends FieldWriter[TextListField, TextListFieldSchema] {
  import TextFieldWriter._
  override def write(
      field: TextListField,
      spec: TextListFieldSchema,
      buffer: LuceneDocument,
      embeddings: Map[String, Array[Float]]
  ): Unit = {
    field.value.foreach(item => {
      if (spec.store) {
        buffer.add(new StoredField(field.name, item))
      }
      if (spec.facet || spec.sort) {
        val trimmed = if (item.length > MAX_FACET_SIZE) item.substring(0, MAX_FACET_SIZE) else item
        buffer.add(new SortedSetDocValuesField(field.name, new BytesRef(trimmed)))
      }
      if (spec.filter || spec.facet) {
        buffer.add(new StringField(field.name + RAW_SUFFIX, item, Store.NO))
      }
      spec.search match {
        case _: LexicalSearch | _: HybridSearch =>
          val trimmed = if (item.length > MAX_FIELD_SEARCH_SIZE) item.substring(0, MAX_FIELD_SEARCH_SIZE) else item
          buffer.add(new org.apache.lucene.document.TextField(field.name, trimmed, Store.NO))
        case _ =>
        // ignore
      }
      spec.suggest.foreach(schema => {
        field.value.foreach(value => {
          SuggestCandidates
            .fromString(schema, spec.name, value)
            .foreach(candidate => {
              val s = SuggestField(field.name, candidate, 1)
              buffer.add(s)
            })
        })
      })

    })
  }
}

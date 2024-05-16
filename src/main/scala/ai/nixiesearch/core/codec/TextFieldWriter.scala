package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema.TextFieldSchema
import ai.nixiesearch.config.mapping.SearchType
import ai.nixiesearch.config.mapping.SearchType.{LexicalSearch, SemanticSearch, SemanticSearchLikeType}
import ai.nixiesearch.core.Field.*
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.{BiEncoderCache, OnnxBiEncoder}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{BinaryDocValuesField, KnnFloatVectorField, SortedDocValuesField, SortedSetDocValuesField, StoredField, StringField, Document as LuceneDocument}
import org.apache.lucene.facet.FacetsConfig
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField
import org.apache.lucene.index.{IndexableField, VectorSimilarityFunction}
import org.apache.lucene.util.BytesRef

import java.util
import scala.runtime.ByteRef

case class TextFieldWriter() extends FieldWriter[TextField, TextFieldSchema] with Logging {
  import TextFieldWriter._
  override def write(
      field: TextField,
      spec: TextFieldSchema,
      buffer: LuceneDocument,
      embeddings: Map[String, Array[Float]]
  ): Unit = {
    if (spec.store) {
      buffer.add(new StoredField(field.name, field.value))
    }
    if (spec.facet || spec.sort) {
      val trimmed = if (field.value.length > MAX_FACET_SIZE) field.value.substring(0, MAX_FACET_SIZE) else field.value
      buffer.add(new SortedDocValuesField(field.name, new BytesRef(field.value)))
    }
    if (spec.filter || spec.facet) {
      buffer.add(new StringField(field.name + RAW_SUFFIX, field.value, Store.NO))
    }
    spec.search match {
      case _: SemanticSearch | _: LexicalSearch =>
        val trimmed =
          if (field.value.length > MAX_FIELD_SEARCH_SIZE) field.value.substring(0, MAX_FIELD_SEARCH_SIZE)
          else field.value
        buffer.add(new org.apache.lucene.document.TextField(field.name, trimmed, Store.NO))

      case _ => //
    }
    spec.search match {
      case SemanticSearchLikeType(model, prefix) =>
        embeddings.get(field.value) match {
          case Some(encoded) =>
            buffer.add(new KnnFloatVectorField(field.name, encoded, VectorSimilarityFunction.COSINE))
          case None => // wtf
        }
      case _ =>
      //
    }
    val br = 1
  }
}

object TextFieldWriter {
  val MAX_FACET_SIZE        = 1024
  val MAX_FIELD_SEARCH_SIZE = 32000
  val RAW_SUFFIX            = "_raw"
}

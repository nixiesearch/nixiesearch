package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.TextListFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.config.mapping.SearchParams.Distance
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.TextLikeField
import ai.nixiesearch.core.codec.FieldCodec
import ai.nixiesearch.core.search.DocumentGroup
import ai.nixiesearch.core.search.DocumentGroup.{PARENT_FIELD, ROLE_FIELD}
import ai.nixiesearch.core.suggest.SuggestCandidates
import io.circe.Decoder.Result
import io.circe.{ACursor, Decoder, DecodingFailure, Json}
import org.apache.lucene.document.{
  KnnFloatVectorField,
  SortedDocValuesField,
  SortedSetDocValuesField,
  StoredField,
  StringField,
  Document as LuceneDocument
}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.index.VectorSimilarityFunction
import org.apache.lucene.search.SortField
import org.apache.lucene.search.suggest.document.SuggestField
import org.apache.lucene.util.BytesRef

case class TextListField(name: String, value: List[String], embeddings: Option[List[Array[Float]]] = None)
    extends Field
    with TextLikeField

object TextListField extends FieldCodec[TextListField, TextListFieldSchema, List[String]] {
  val NESTED_EMBED_SUFFIX = "._nested"
  import TextField.{MAX_FACET_SIZE, FILTER_SUFFIX, MAX_FIELD_SEARCH_SIZE}

  def apply(name: String, value: String, values: String*) = new TextListField(name, value +: values.toList)
  def apply(name: String, values: List[String])           = new TextListField(name, values)

  override def writeLucene(
      field: TextListField,
      spec: TextListFieldSchema,
      buffer: DocumentGroup
  ): Unit = {
    field.value.foreach(item => {
      if (spec.store) {
        buffer.parent.add(new StoredField(field.name, item))
      }
      lazy val trimmed = if (item.length > MAX_FACET_SIZE) item.substring(0, MAX_FACET_SIZE) else item
      if (spec.facet) {
        buffer.parent.add(new SortedSetDocValuesField(field.name, new BytesRef(trimmed)))
      }
      if (spec.sort) {
        buffer.parent.add(new SortedDocValuesField(field.name + SORT_SUFFIX, new BytesRef(trimmed)))
      }
      if (spec.filter || spec.facet) {
        buffer.parent.add(new StringField(field.name + FILTER_SUFFIX, item, Store.NO))
      }
      if (spec.search.lexical.isDefined) {
        val searchTrimmed = if (item.length > MAX_FIELD_SEARCH_SIZE) item.substring(0, MAX_FIELD_SEARCH_SIZE) else item
        buffer.parent.add(new org.apache.lucene.document.TextField(field.name, searchTrimmed, Store.NO))
      }
    })

    for {
      conf <- spec.search.semantic
      similarityFunction = conf.distance match {
        case Distance.Cosine => VectorSimilarityFunction.COSINE
        case Distance.Dot    => VectorSimilarityFunction.DOT_PRODUCT
      }

      embeds        <- field.embeddings
      (text, embed) <- field.value.zip(embeds)
    } {
      val child = new LuceneDocument()
      child.add(new StringField(PARENT_FIELD, field.name, Store.YES))
      child.add(new KnnFloatVectorField(field.name + NESTED_EMBED_SUFFIX, embed, similarityFunction))
      buffer.children.addOne(child)
    }

    spec.suggest.foreach(schema => {
      field.value.foreach(value => {
        SuggestCandidates
          .fromString(schema, field.name, value)
          .foreach(candidate => {
            val s = new SuggestField(field.name + TextField.SUGGEST_SUFFIX, candidate, 1)
            buffer.parent.add(s)
          })
      })
    })
  }

  override def readLucene(
      name: String,
      spec: TextListFieldSchema,
      value: List[String]
  ): Either[FieldCodec.WireDecodingError, TextListField] =
    Right(TextListField(name, value))

  override def encodeJson(field: TextListField): Json = Json.fromValues(field.value.map(Json.fromString))

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): SortField = {
    val sortField = new SortField(field.name + SORT_SUFFIX, SortField.Type.STRING, reverse)
    sortField.setMissingValue(
      MissingValue.of(min = SortField.STRING_FIRST, max = SortField.STRING_LAST, reverse, missing)
    )
    sortField
  }

}

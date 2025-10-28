package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.{FloatFieldSchema, FloatListFieldSchema}
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.NumericField
import ai.nixiesearch.core.codec.FieldCodec
import ai.nixiesearch.core.search.DocumentGroup
import io.circe.{Decoder, Json}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{
  NumericDocValuesField,
  SortedNumericDocValuesField,
  StoredField,
  Document as LuceneDocument
}
import org.apache.lucene.search.SortField
import org.apache.lucene.util.NumericUtils

case class FloatListField(name: String, value: List[Float]) extends Field with NumericField

object FloatListField extends FieldCodec[FloatListField, FloatListFieldSchema, List[Float]] {
  override def writeLucene(
      field: FloatListField,
      spec: FloatListFieldSchema,
      buffer: DocumentGroup
  ): Unit = {
    if (spec.filter) {
      field.value.foreach(value =>
        buffer.parent.add(new org.apache.lucene.document.FloatField(field.name, value, Store.NO))
      )
    }
    if (spec.store) {
      field.value.foreach(value => buffer.parent.add(new StoredField(field.name, value)))
    }
    if (spec.facet) {
      field.value.foreach(value =>
        buffer.parent.add(new SortedNumericDocValuesField(field.name, NumericUtils.doubleToSortableLong(value)))
      )
    }

  }

  override def readLucene(
      name: String,
      spec: FloatListFieldSchema,
      value: List[Float]
  ): Either[FieldCodec.WireDecodingError, FloatListField] =
    Right(FloatListField(name, value))

  override def encodeJson(field: FloatListField): Json = Json.fromValues(field.value.map(Json.fromFloatOrNull))

  override def decodeJson(spec: FloatListFieldSchema): Decoder[Option[FloatListField]] =
    Decoder.instance(
      _.downField(spec.name.name)
        .as[Option[List[Float]]]
        .map {
          case Some(Nil) => None
          case Some(nel) => Some(FloatListField(spec.name.name, nel))
          case None      => None
        }
    )

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): SortField = ???

}

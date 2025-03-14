package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.config.FieldSchema.BooleanFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.codec.FieldCodec
import ai.nixiesearch.core.codec.FieldCodec.WireDecodingError
import io.circe.Decoder.Result
import io.circe.{ACursor, Json}
import org.apache.lucene.document.{
  NumericDocValuesField,
  SortedNumericDocValuesField,
  StoredField,
  Document as LuceneDocument
}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.search.SortField

case class BooleanField(name: String, value: Boolean) extends Field {
  def intValue: Int = if (value) 1 else 0
}

object BooleanField extends FieldCodec[BooleanField, BooleanFieldSchema, Int] {
  override def writeLucene(
      field: BooleanField,
      spec: BooleanFieldSchema,
      buffer: LuceneDocument,
      embeddings: Map[String, Array[Float]] = Map.empty
  ): Unit = {
    if (spec.filter) {
      buffer.add(new org.apache.lucene.document.IntField(field.name, toInt(field.value), Store.NO))
    }
    if (spec.sort) {
      buffer.add(new NumericDocValuesField(field.name + SORT_SUFFIX, toInt(field.value)))
    }

    if (spec.facet) {
      buffer.add(new SortedNumericDocValuesField(field.name, toInt(field.value)))
    }
    if (spec.store) {
      buffer.add(new StoredField(field.name, toInt(field.value)))
    }
  }

  override def readLucene(name: String, spec: BooleanFieldSchema, value: Int): Either[WireDecodingError, BooleanField] =
    fromInt(value).map(bool => BooleanField(name, bool))

  private def toInt(bool: Boolean): Int = if (bool) 1 else 0
  private def fromInt(value: Int): Either[WireDecodingError, Boolean] = value match {
    case 0     => Right(false)
    case 1     => Right(true)
    case other => Left(WireDecodingError(s"cannot decode int value of ${other} as boolean"))
  }

  override def encodeJson(field: BooleanField): Json = Json.fromBoolean(field.value)

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): SortField =
    IntField.sort(field, reverse, missing)
}

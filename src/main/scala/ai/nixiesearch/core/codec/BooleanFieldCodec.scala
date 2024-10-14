package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema.BooleanFieldSchema
import ai.nixiesearch.core.Field.BooleanField
import ai.nixiesearch.core.codec.FieldCodec.WireDecodingError
import org.apache.lucene.document.{SortedNumericDocValuesField, StoredField}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.Document as LuceneDocument

object BooleanFieldCodec extends FieldCodec[BooleanField, BooleanFieldSchema, Int] {
  override def write(
      field: BooleanField,
      spec: BooleanFieldSchema,
      buffer: LuceneDocument,
      embeddings: Map[String, Array[Float]] = Map.empty
  ): Unit = {
    if (spec.filter || spec.sort) {
      buffer.add(new org.apache.lucene.document.IntField(field.name, toInt(field.value), Store.NO))
    }
    if (spec.facet) {
      buffer.add(new SortedNumericDocValuesField(field.name, toInt(field.value)))
    }
    if (spec.store) {
      buffer.add(new StoredField(field.name, toInt(field.value)))
    }
  }

  override def read(spec: BooleanFieldSchema, value: Int): Either[WireDecodingError, BooleanField] =
    fromInt(value).map(bool => BooleanField(spec.name, bool))

  private def toInt(bool: Boolean): Int = if (bool) 1 else 0
  private def fromInt(value: Int): Either[WireDecodingError, Boolean] = value match {
    case 0     => Right(false)
    case 1     => Right(true)
    case other => Left(WireDecodingError(s"cannot decode int value of ${other} as boolean"))
  }
}

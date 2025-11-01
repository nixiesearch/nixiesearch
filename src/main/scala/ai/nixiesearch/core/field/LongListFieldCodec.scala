package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.{IntListFieldSchema, LongListFieldSchema}
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.{DocumentDecoder, Field}
import ai.nixiesearch.core.Field.{LongListField, NumericField}
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.codec.DocumentVisitor.StoredLuceneField.LongStoredField
import ai.nixiesearch.core.search.DocumentGroup
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.circe.{Decoder, Json}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{SortedNumericDocValuesField, StoredField, Document as LuceneDocument}
import org.apache.lucene.search.SortField

case class LongListFieldCodec(spec: LongListFieldSchema) extends FieldCodec[LongListField] {
  override def writeLucene(
      field: LongListField,
      buffer: DocumentGroup
  ): Unit = {
    if (spec.filter) {
      field.value.foreach(value =>
        buffer.parent.add(new org.apache.lucene.document.LongField(field.name, value, Store.NO))
      )
    }
    if (spec.store) {
      field.value.foreach(value => buffer.parent.add(new StoredField(field.name, value)))
    }
    if (spec.facet) {
      field.value.foreach(value => buffer.parent.add(new SortedNumericDocValuesField(field.name, value)))
    }

  }

  override def readLucene(
      doc: DocumentVisitor.StoredDocument
  ): Either[FieldCodec.WireDecodingError, Option[LongListField]] =
    doc.fields.collect { case f @ LongStoredField(name, value) if spec.name.matches(name) => f } match {
      case Nil             => Right(None)
      case all @ head :: _ => Right(Some(LongListField(head.name, all.map(_.value))))
    }

  override def encodeJson(field: LongListField): Json = Json.fromValues(field.value.map(Json.fromLong))

  override def decodeJson(name: String, reader: JsonReader): Either[DocumentDecoder.JsonError, Option[LongListField]] =
    decodeJsonImpl(name, () => LongListFieldCodec.longListCodec.decodeValue(reader, null)).map {
      case Nil => None
      case nel => Some(LongListField(name, nel))
    }

  def sort(field: FieldName, reverse: Boolean, missing: MissingValue): Either[BackendError, SortField] = Left(
    BackendError("cannot sort on long[] field")
  )

}

object LongListFieldCodec {
  val longListCodec: JsonValueCodec[List[Long]] = JsonCodecMaker.make[List[Long]]
}

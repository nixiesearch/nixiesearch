package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.IntListFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.{DocumentDecoder, Field}
import ai.nixiesearch.core.Field.{FloatListField, IntListField, NumericField}
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.codec.DocumentVisitor.StoredLuceneField.IntStoredField
import ai.nixiesearch.core.search.DocumentGroup
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.circe.{Decoder, Json}
import org.apache.lucene.document.{
  NumericDocValuesField,
  SortedNumericDocValuesField,
  StoredField,
  Document as LuceneDocument
}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.search.SortField

case class IntListFieldCodec(spec: IntListFieldSchema) extends FieldCodec[IntListField] {
  override def writeLucene(
      field: IntListField,
      buffer: DocumentGroup
  ): Unit = {
    if (spec.filter) {
      field.value.foreach(value =>
        buffer.parent.add(new org.apache.lucene.document.IntField(field.name, value, Store.NO))
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
  ): Either[FieldCodec.WireDecodingError, Option[IntListField]] =
    doc.fields.collect { case f @ IntStoredField(name, value) if spec.name.matches(StringName(name)) => f } match {
      case Nil             => Right(None)
      case all @ head :: _ => Right(Some(IntListField(head.name, all.map(_.value))))
    }

  override def encodeJson(field: IntListField): Json = Json.fromValues(field.value.map(Json.fromInt))

  override def decodeJson(name: String, reader: JsonReader): Either[DocumentDecoder.JsonError, Option[IntListField]] =
    decodeJsonImpl(name, () => IntListFieldCodec.intlistCodec.decodeValue(reader, null)).map {
      case Nil => None
      case nel => Some(IntListField(name, nel))
    }

  def sort(field: FieldName, reverse: Boolean, missing: MissingValue): Either[BackendError, SortField] = Left(
    BackendError("cannot sort on int[] field")
  )

}

object IntListFieldCodec {
  val intlistCodec: JsonValueCodec[List[Int]] = JsonCodecMaker.make[List[Int]]
}

package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.{DoubleFieldSchema, DoubleListFieldSchema}
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.{DocumentDecoder, Field}
import ai.nixiesearch.core.Field.{DoubleListField, NumericField}
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.codec.DocumentVisitor.StoredLuceneField.DoubleStoredField
import ai.nixiesearch.core.search.DocumentGroup
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import io.circe.{Decoder, Json}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{Document, NumericDocValuesField, SortedNumericDocValuesField, StoredField}
import org.apache.lucene.search.SortField
import org.apache.lucene.util.NumericUtils

case class DoubleListFieldCodec(spec: DoubleListFieldSchema) extends FieldCodec[DoubleListField] {
  override def writeLucene(
      field: DoubleListField,
      buffer: DocumentGroup
  ): Unit = {
    if (spec.filter) {
      field.value.foreach(value =>
        buffer.parent.add(new org.apache.lucene.document.DoubleField(field.name, value, Store.NO))
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
      doc: DocumentVisitor.StoredDocument
  ): Either[FieldCodec.WireDecodingError, Option[DoubleListField]] =
    doc.fields.collect { case f @ DoubleStoredField(name, value) if spec.name.matches(name) => f } match {
      case Nil             => Right(None)
      case all @ head :: _ => Right(Some(DoubleListField(head.name, all.map(_.value))))
    }

  override def encodeJson(field: DoubleListField): Json = Json.fromValues(field.value.map(Json.fromDoubleOrNull))

  override def decodeJson(name: String, reader: JsonReader): Either[DocumentDecoder.JsonError, DoubleListField] =
    ???

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): Either[BackendError, SortField] =
    Left(
      BackendError("cannot sort on double[] field")
    )

}

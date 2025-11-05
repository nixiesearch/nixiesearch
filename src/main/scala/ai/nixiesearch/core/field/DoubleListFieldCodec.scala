package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.{DoubleFieldSchema, DoubleListFieldSchema}
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.DocumentDecoder.JsonError
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.{DocumentDecoder, Field}
import ai.nixiesearch.core.Field.{DoubleListField, NumericField}
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.codec.DocumentVisitor.StoredLuceneField.DoubleStoredField
import ai.nixiesearch.core.search.DocumentGroup
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.circe.{Decoder, Json}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{Document, NumericDocValuesField, SortedNumericDocValuesField, StoredField}
import org.apache.lucene.search.SortField
import org.apache.lucene.util.NumericUtils

import scala.util.{Failure, Success, Try}

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
  ): Either[FieldCodec.WireDecodingError, List[DoubleListField]] =
    doc.fields.collect { case f @ DoubleStoredField(name, value) if spec.name.matches(StringName(name)) => f } match {
      case Nil             => Right(Nil)
      case all @ head :: _ => Right(List(DoubleListField(head.name, all.map(_.value))))
    }

  override def encodeJson(field: DoubleListField): Json = Json.fromValues(field.value.map(Json.fromDoubleOrNull))

  override def decodeJson(
      name: String,
      reader: JsonReader
  ): Either[DocumentDecoder.JsonError, Option[DoubleListField]] = {
    decodeJsonImpl(name, () => DoubleListFieldCodec.doubleListCodec.decodeValue(reader, null)).map {
      case Nil => None
      case nel => Some(DoubleListField(name, nel))
    }
  }

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): Either[BackendError, SortField] =
    Left(
      BackendError("cannot sort on double[] field")
    )

}

object DoubleListFieldCodec {
  val doubleListCodec: JsonValueCodec[List[Double]] = JsonCodecMaker.make[List[Double]]
}

package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.{FloatFieldSchema, FloatListFieldSchema}
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.{DocumentDecoder, Field}
import ai.nixiesearch.core.Field.{FloatListField, NumericField}
import ai.nixiesearch.core.codec.DocumentVisitor.StoredDocument
import ai.nixiesearch.core.codec.DocumentVisitor.StoredLuceneField.FloatStoredField
import ai.nixiesearch.core.search.DocumentGroup
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
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

case class FloatListFieldCodec(spec: FloatListFieldSchema) extends FieldCodec[FloatListField] {
  override def writeLucene(
      field: FloatListField,
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

  override def readLucene(doc: StoredDocument): Either[FieldCodec.WireDecodingError, List[FloatListField]] =
    doc.fields.collect { case f @ FloatStoredField(name, value) if spec.name.matches(StringName(name)) => f } match {
      case Nil             => Right(Nil)
      case all @ head :: _ => Right(List(FloatListField(head.name, all.map(_.value))))
    }

  override def encodeJson(field: FloatListField): Json = Json.fromValues(field.value.map(Json.fromFloatOrNull))

  override def decodeJson(name: String, reader: JsonReader): Either[DocumentDecoder.JsonError, Option[FloatListField]] =
    decodeJsonImpl(name, () => FloatListFieldCodec.floatListCodec.decodeValue(reader, null)).map {
      case Nil => None
      case nel => Some(FloatListField(name, nel))
    }

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): Either[BackendError, SortField] =
    Left(BackendError("cannot sort on float[] field"))

}

object FloatListFieldCodec {
  val floatListCodec: JsonValueCodec[List[Float]] = JsonCodecMaker.make[List[Float]]
}

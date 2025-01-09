package ai.nixiesearch.core.field

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextLikeFieldSchema}
import ai.nixiesearch.config.mapping.{Language, SearchType, SuggestSchema}
import ai.nixiesearch.config.mapping.SearchType.{LexicalSearch, NoSearch, SemanticSearch, SemanticSearchLikeType}
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.TextLikeField
import ai.nixiesearch.core.codec.FieldCodec
import ai.nixiesearch.core.suggest.SuggestCandidates
import io.circe.Decoder.Result
import io.circe.{ACursor, Json}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{
  KnnFloatVectorField,
  SortedDocValuesField,
  StoredField,
  StringField,
  Document as LuceneDocument
}
import org.apache.lucene.index.VectorSimilarityFunction
import org.apache.lucene.search.suggest.document.SuggestField
import org.apache.lucene.util.BytesRef

import java.util.UUID

case class TextField(name: String, value: String) extends Field with TextLikeField

object TextField extends FieldCodec[TextField, TextFieldSchema, String] {
  val MAX_FACET_SIZE        = 1024
  val MAX_FIELD_SEARCH_SIZE = 32000
  val RAW_SUFFIX            = "$raw"
  val SUGGEST_SUFFIX        = "$suggest"

  override def writeLucene(
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
      case SemanticSearchLikeType(model) =>
        embeddings.get(field.value) match {
          case Some(encoded) =>
            buffer.add(new KnnFloatVectorField(field.name, encoded, VectorSimilarityFunction.COSINE))
          case None => // wtf
        }
      case _ =>
      //
    }
    spec.suggest.foreach(schema => {
      SuggestCandidates
        .fromString(schema, field.name, field.value)
        .foreach(candidate => {
          val s = SuggestField(field.name + SUGGEST_SUFFIX, candidate, 1)
          buffer.add(s)
        })
    })
    val br = 1
  }

  override def readLucene(
      name: String,
      spec: TextFieldSchema,
      value: String
  ): Either[FieldCodec.WireDecodingError, TextField] =
    Right(TextField(name, value))

  override def encodeJson(field: TextField): Json = Json.fromString(field.value)

  override def decodeJson(name: String, schema: TextFieldSchema, json: Json): Result[Option[TextField]] = {
    val parts = name.split('.').toList
    if (name == "_id") {
      decodeRecursiveScalar[String](parts, schema, json, _.as[Option[String]], TextField(name, _)) match {
        case Left(_) | Right(None) =>
          decodeRecursiveScalar[Long](
            parts,
            schema,
            json,
            _.as[Option[Long]],
            (x: Long) => TextField(name, x.toString)
          ) match {
            case Left(err)    => Left(err)
            case Right(None)  => Right(Some(TextField("_id", UUID.randomUUID().toString)))
            case Right(value) => Right(value)
          }
        case Right(value) => Right(value)
      }
    } else {
      decodeRecursiveScalar[String](parts, schema, json, _.as[Option[String]], TextField(name, _))
    }

  }

}

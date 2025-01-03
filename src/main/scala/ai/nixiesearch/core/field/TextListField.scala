package ai.nixiesearch.core.field

import ai.nixiesearch.config.FieldSchema.TextListFieldSchema
import ai.nixiesearch.config.mapping.SearchType.{HybridSearch, LexicalSearch}
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.TextLikeField
import ai.nixiesearch.core.codec.FieldCodec
import ai.nixiesearch.core.suggest.SuggestCandidates
import io.circe.Decoder.Result
import io.circe.{ACursor, Decoder, DecodingFailure, Json}
import org.apache.lucene.document.{SortedSetDocValuesField, StoredField, StringField, Document as LuceneDocument}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.search.suggest.document.SuggestField
import org.apache.lucene.util.BytesRef

case class TextListField(name: String, value: List[String]) extends Field with TextLikeField

object TextListField extends FieldCodec[TextListField, TextListFieldSchema, List[String]] {
  import TextField.{MAX_FACET_SIZE, RAW_SUFFIX, MAX_FIELD_SEARCH_SIZE}

  def apply(name: String, value: String, values: String*) = new TextListField(name, value +: values.toList)

  override def writeLucene(
      field: TextListField,
      spec: TextListFieldSchema,
      buffer: LuceneDocument,
      embeddings: Map[String, Array[Float]]
  ): Unit = {
    field.value.foreach(item => {
      if (spec.store) {
        buffer.add(new StoredField(field.name, item))
      }
      if (spec.facet || spec.sort) {
        val trimmed = if (item.length > MAX_FACET_SIZE) item.substring(0, MAX_FACET_SIZE) else item
        buffer.add(new SortedSetDocValuesField(field.name, new BytesRef(trimmed)))
      }
      if (spec.filter || spec.facet) {
        buffer.add(new StringField(field.name + RAW_SUFFIX, item, Store.NO))
      }
      spec.search match {
        case _: LexicalSearch | _: HybridSearch =>
          val trimmed = if (item.length > MAX_FIELD_SEARCH_SIZE) item.substring(0, MAX_FIELD_SEARCH_SIZE) else item
          buffer.add(new org.apache.lucene.document.TextField(field.name, trimmed, Store.NO))
        case _ =>
        // ignore
      }
      spec.suggest.foreach(schema => {
        field.value.foreach(value => {
          SuggestCandidates
            .fromString(schema, spec.name, value)
            .foreach(candidate => {
              val s = SuggestField(field.name, candidate, 1)
              buffer.add(s)
            })
        })
      })

    })
  }

  override def readLucene(
      spec: TextListFieldSchema,
      value: List[String]
  ): Either[FieldCodec.WireDecodingError, TextListField] =
    Right(TextListField(spec.name, value))

  override def encodeJson(field: TextListField): Json = Json.fromValues(field.value.map(Json.fromString))

  override def decodeJson(schema: TextListFieldSchema, cursor: ACursor): Result[Option[TextListField]] = {
    val parts = schema.name.split('.').toList
    decodeRecursive(parts, schema, cursor.focus.get, cursor, Nil) match {
      case Right(Nil)      => Right(None)
      case Right(nonEmpty) => Right(Some(TextListField(schema.name, nonEmpty)))
      case Left(err)       => Left(err)
    }
  }

  private def decodeRecursive(
      parts: List[String],
      schema: TextListFieldSchema,
      json: Json,
      cursor: ACursor,
      acc: List[String]
  ): Result[List[String]] = parts match {
    case head :: tail =>
      if (json.isObject) {
        json.asObject.flatMap(_.apply(head)) match {
          case Some(value) => decodeRecursive(tail, schema, value, cursor, acc)
          case None        => Right(Nil)
        }
      } else if (json.isArray) {
        json.asArray.toList.flatten.foldLeft[Decoder.Result[List[String]]](Right(Nil)) {
          case (Right(list), next) =>
            decodeRecursive(head :: tail, schema, next, cursor, list) match {
              case Left(err)    => Left(err)
              case Right(value) => Right(list ++ value)
            }
          case (Left(err), _) => Left(err)
        }
      } else {
        Left(
          DecodingFailure(
            s"for text[] field ${schema.name} we expect root obj/array json value, but got $json",
            cursor.history
          )
        )
      }
    case Nil =>
      if (json.isString) {
        Right(json.asString.toList)
      } else if (json.isArray) {
        json.as[List[String]]
      } else {
        Left(
          DecodingFailure(
            s"for text[] field ${schema.name} we expect string/string[] json value, but got $json",
            cursor.history
          )
        )
      }
  }

}

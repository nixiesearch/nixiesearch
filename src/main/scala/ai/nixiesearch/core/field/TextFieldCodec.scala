package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextLikeFieldSchema}
import ai.nixiesearch.config.mapping.{FieldName, Language, SearchParams, SuggestSchema}
import ai.nixiesearch.config.mapping.SearchParams.*
import ai.nixiesearch.core.DocumentDecoder.JsonError
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.{TextField, TextLikeField}
import FieldCodec.WireDecodingError
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.search.DocumentGroup
import ai.nixiesearch.core.suggest.SuggestCandidates
import com.github.plokhotnyuk.jsoniter_scala.circe.CirceCodecs
import io.circe.Decoder.Result
import io.circe.{ACursor, Codec, Decoder, DecodingFailure, Encoder, Json, JsoniterScalaCodec}
import io.circe.generic.semiauto.*
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{
  BinaryDocValuesField,
  KnnFloatVectorField,
  SortedDocValuesField,
  StoredField,
  StringField,
  Document as LuceneDocument
}
import org.apache.lucene.index.VectorSimilarityFunction
import org.apache.lucene.search.SortField
import org.apache.lucene.search.suggest.document.SuggestField
import org.apache.lucene.util.BytesRef
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec.*

import java.util.UUID
import com.github.plokhotnyuk.jsoniter_scala.circe.CirceCodecs.*
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

import scala.util.{Failure, Success, Try}

case class TextFieldCodec(spec: TextFieldSchema) extends FieldCodec[TextField] {
  import TextFieldCodec.*
  import FieldCodec.*

  override def writeLucene(
      field: TextField,
      buffer: DocumentGroup
  ): Unit = {

    if (spec.store) {
      buffer.parent.add(new StoredField(field.name, field.value))
    }
    if (spec.facet || spec.sort) {
      val trimmed = if (field.value.length > MAX_FACET_SIZE) field.value.substring(0, MAX_FACET_SIZE) else field.value
      buffer.parent.add(new SortedDocValuesField(field.name, new BytesRef(trimmed)))
    }
    if (spec.filter) {
      buffer.parent.add(new StringField(field.name + FILTER_SUFFIX, field.value, Store.NO))

    }
    if (spec.search.lexical.isDefined) {
      val trimmed =
        if (field.value.length > MAX_FIELD_SEARCH_SIZE) field.value.substring(0, MAX_FIELD_SEARCH_SIZE)
        else field.value
      buffer.parent.add(new org.apache.lucene.document.TextField(field.name, trimmed, Store.NO))
    }

    spec.search.semantic.foreach(conf =>
      field.embedding match {
        case Some(embed) =>
          val similarityFunction = conf.distance match {
            case Distance.Cosine => VectorSimilarityFunction.COSINE
            case Distance.Dot    => VectorSimilarityFunction.DOT_PRODUCT
          }
          buffer.parent.add(new KnnFloatVectorField(field.name, embed, similarityFunction))
        case None => logger.warn(s"field ${field.name} should have an embedding, but it has not - a bug?")
      }
    )

    spec.suggest.foreach(schema => {
      SuggestCandidates
        .fromString(schema, field.name, field.value)
        .foreach(candidate => {
          val s = SuggestField(field.name + SUGGEST_SUFFIX, candidate, 1)
          buffer.parent.add(s)
        })
    })

  }

  override def readLucene(doc: DocumentVisitor.StoredDocument): Either[WireDecodingError, Option[TextField]] = ???

  override def encodeJson(field: TextField): Json = Json.fromString(field.value)

  override def decodeJson(name: String, reader: JsonReader): Either[JsonError, TextField] = {
    val tok = reader.nextToken()
    reader.rollbackToken()
    tok match {
      case '"'   => decodePlain(name, reader)
      case '{'   => decodeEmbed(name, reader)
      case other => Left(JsonError(s"""for field $name expected '"' or '{' tokens"""))
    }
  }

  def decodePlain(name: String, in: JsonReader): Either[JsonError, TextField] =
    Try(in.readString(null)) match {
      case Success(null) => Left(JsonError(s"field $name: got null"))
      case Success(str)  => Right(TextField(name, str, None))
      case Failure(err)  => Left(JsonError(s"field $name: cannot parse string: $err", err))
    }
  val textEmbeddingJICodec                                                    = JsonCodecMaker.make[TextEmbedding]
  def decodeEmbed(name: String, in: JsonReader): Either[JsonError, TextField] = {
    spec.search.semantic match {
      case Some(s: SemanticParams) =>
        Try(textEmbeddingJICodec.decodeValue(in, null)) match {
          case Failure(err)  => Left(JsonError(s"field $name: cannot parse string: $err", err))
          case Success(null) => Left(JsonError(s"field $name: got null"))
          case Success(emb) if emb.embedding.length != s.dim =>
            Left(JsonError(s"field $name: expected dim ${s.dim}, but got ${emb.embedding.length}"))
          case Success(emb) => Right(TextField(name, emb.text, Some(emb.embedding)))
        }
      case None =>
        Left(JsonError(s"field ${name} has semantic=false for search params"))
    }
  }

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): Either[BackendError, SortField] = {
    val sortField = new SortField(field.name, SortField.Type.STRING, reverse)
    sortField.setMissingValue(
      MissingValue.of(min = SortField.STRING_FIRST, max = SortField.STRING_LAST, reverse, missing)
    )
    Right(sortField)
  }
}

object TextFieldCodec {
  val MAX_FACET_SIZE        = 1024
  val MAX_FIELD_SEARCH_SIZE = 32000
  case class TextEmbedding(text: String, embedding: Array[Float])
  given textEmbeddingCodec: Codec[TextEmbedding] = deriveCodec

}

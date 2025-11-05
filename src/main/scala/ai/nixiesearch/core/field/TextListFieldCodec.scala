package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate
import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema.TextListFieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.SearchParams.Distance
import ai.nixiesearch.core.DocumentDecoder.JsonError
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.{DocumentDecoder, Field}
import ai.nixiesearch.core.Field.{TextLikeField, TextListField}
import ai.nixiesearch.core.codec.DocumentVisitor
import ai.nixiesearch.core.codec.DocumentVisitor.StoredLuceneField.StringStoredField
import ai.nixiesearch.core.search.DocumentGroup
import ai.nixiesearch.core.search.DocumentGroup.{PARENT_FIELD, ROLE_FIELD}
import ai.nixiesearch.core.suggest.SuggestCandidates
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.circe.Decoder.Result
import io.circe.{ACursor, Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.*
import org.apache.lucene.document.{
  KnnFloatVectorField,
  SortedDocValuesField,
  SortedSetDocValuesField,
  StoredField,
  StringField,
  Document as LuceneDocument
}
import org.apache.lucene.document.Field.Store
import org.apache.lucene.index.VectorSimilarityFunction
import org.apache.lucene.search.SortField
import org.apache.lucene.search.suggest.document.SuggestField
import org.apache.lucene.util.BytesRef

import scala.util.{Failure, Success, Try}

case class TextListFieldCodec(spec: TextListFieldSchema) extends FieldCodec[TextListField] {
  import TextFieldCodec.*
  import FieldCodec.*
  import TextListFieldCodec.*

  import TextFieldCodec.{MAX_FACET_SIZE, MAX_FIELD_SEARCH_SIZE}

  override def decodeJson(
      name: String,
      reader: JsonReader
  ): Either[DocumentDecoder.JsonError, Option[TextListField]] = {
    val first = reader.nextToken()
    reader.rollbackToken()
    first match {
      case '[' =>
        decodeJsonImpl(name, () => TextListFieldCodec.textListCodec.decodeValue(reader, null)).map {
          case value if value.isEmpty => None
          case value                  => Some(TextListField(name, value))
        }
      case '{' =>
        decodeJsonImpl(name, () => TextListFieldCodec.textListEmbCodec.decodeValue(reader, null)).flatMap {
          case value if value.text.isEmpty                               => Right(None)
          case value if value.text.size == 1 && value.embedding.size > 1 =>
            Right(Some(TextListField(name, value.text, Some(value.embedding))))
          case value if value.text.size == value.embedding.size =>
            Right(Some(TextListField(name, value.text, Some(value.embedding))))
          case value =>
            Left(JsonError(s"field $name: len(text)=${value.text.size} len(embed)=${value.embedding.size} mismatch"))
        }
    }
  }

  override def writeLucene(
      field: TextListField,
      buffer: DocumentGroup
  ): Unit = {
    field.value.foreach(item => {
      if (spec.store) {
        buffer.parent.add(new StoredField(field.name, item))
      }
      lazy val trimmed = if (item.length > MAX_FACET_SIZE) item.substring(0, MAX_FACET_SIZE) else item
      if (spec.facet) {
        buffer.parent.add(new SortedSetDocValuesField(field.name, new BytesRef(trimmed)))
      }
      if (spec.sort) {
        buffer.parent.add(new SortedDocValuesField(field.name + SORT_SUFFIX, new BytesRef(trimmed)))
      }
      if (spec.filter || spec.facet) {
        buffer.parent.add(new StringField(field.name + FILTER_SUFFIX, item, Store.NO))
      }
      if (spec.search.lexical.isDefined) {
        val searchTrimmed = if (item.length > MAX_FIELD_SEARCH_SIZE) item.substring(0, MAX_FIELD_SEARCH_SIZE) else item
        buffer.parent.add(new org.apache.lucene.document.TextField(field.name, searchTrimmed, Store.NO))
      }
    })

    for {
      conf <- spec.search.semantic
      similarityFunction = conf.distance match {
        case Distance.Cosine => VectorSimilarityFunction.COSINE
        case Distance.Dot    => VectorSimilarityFunction.DOT_PRODUCT
      }

      embeds        <- field.embeddings
      (text, embed) <- field.value.zip(embeds)
    } {
      val child = new LuceneDocument()
      child.add(new StringField(PARENT_FIELD, field.name, Store.YES))
      child.add(new KnnFloatVectorField(field.name + NESTED_EMBED_SUFFIX, embed, similarityFunction))
      buffer.children.addOne(child)
    }

    spec.suggest.foreach(schema => {
      field.value.foreach(value => {
        SuggestCandidates
          .fromString(schema, field.name, value)
          .foreach(candidate => {
            val s = new SuggestField(field.name + FieldCodec.SUGGEST_SUFFIX, candidate, 1)
            buffer.parent.add(s)
          })
      })
    })
  }

  override def readLucene(doc: DocumentVisitor.StoredDocument): Either[WireDecodingError, List[TextListField]] =
    doc.fields.collect { case f @ StringStoredField(name, value) if spec.name.matches(StringName(name)) => f } match {
      case Nil             => Right(Nil)
      case all @ head :: _ => Right(List(TextListField(head.name, all.map(_.value))))
    }

  override def encodeJson(field: TextListField): Json = Json.fromValues(field.value.map(Json.fromString))

  def sort(field: FieldName, reverse: Boolean, missing: SortPredicate.MissingValue): Either[BackendError, SortField] = {
    val sortField = new SortField(field.name + SORT_SUFFIX, SortField.Type.STRING, reverse)
    sortField.setMissingValue(
      MissingValue.of(min = SortField.STRING_FIRST, max = SortField.STRING_LAST, reverse, missing)
    )
    Right(sortField)
  }

}

object TextListFieldCodec {
  val NESTED_EMBED_SUFFIX = "._nested"
  case class TextListEmbedding(text: List[String], embedding: List[Array[Float]])

  val textListCodec    = JsonCodecMaker.make[List[String]]
  val textListEmbCodec = JsonCodecMaker.make[TextListEmbedding]

}

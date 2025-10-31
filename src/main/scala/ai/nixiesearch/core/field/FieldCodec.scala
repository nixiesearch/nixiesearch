package ai.nixiesearch.core.field

import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.DocumentDecoder.JsonError
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.codec.DocumentVisitor.StoredDocument
import FieldCodec.WireDecodingError
import ai.nixiesearch.core.search.DocumentGroup
import ai.nixiesearch.core.{Field, Logging}
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import io.circe.Decoder.Result
import io.circe.{ACursor, Decoder, DecodingFailure, Json}
import org.apache.lucene.document.Document as LuceneDocument
import org.apache.lucene.search.SortField

import scala.annotation.tailrec

trait FieldCodec[T <: Field] extends Logging {

  def writeLucene(field: T, buffer: DocumentGroup): Unit
  def readLucene(doc: StoredDocument): Either[WireDecodingError, Option[T]]
  def encodeJson(field: T): Json
  def decodeJson(name: String, reader: JsonReader): Either[JsonError, T]
  def sort(field: FieldName, reverse: Boolean, missing: MissingValue): Either[BackendError, SortField]

}

object FieldCodec {
  val FILTER_SUFFIX  = "$raw"
  val SORT_SUFFIX    = "$sort"
  val SUGGEST_SUFFIX = "$suggest"
  case class WireDecodingError(msg: String) extends Exception(msg)
}

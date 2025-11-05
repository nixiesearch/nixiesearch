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
import scala.util.{Failure, Success, Try}

trait FieldCodec[T <: Field] extends Logging {

  def writeLucene(field: T, buffer: DocumentGroup): Unit
  def readLucene(doc: StoredDocument): Either[WireDecodingError, List[T]]
  def encodeJson(field: T): Json
  def decodeJson(name: String, reader: JsonReader): Either[JsonError, Option[T]]
  def sort(field: FieldName, reverse: Boolean, missing: MissingValue): Either[BackendError, SortField]

  protected def decodeJsonImpl[U](name: String, decode: () => U): Either[JsonError, U] =
    Try(decode()) match {
      case Failure(exception) => Left(JsonError(s"field $name: cannot decode", exception))
      case Success(null)      => Left(JsonError("field $name: got null"))
      case Success(value)     => Right(value)
    }

  
}

object FieldCodec {
  val FILTER_SUFFIX  = "$raw"
  val SORT_SUFFIX    = "$sort"
  val SUGGEST_SUFFIX = "$suggest"
  case class WireDecodingError(msg: String) extends Exception(msg)
}

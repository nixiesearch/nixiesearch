package ai.nixiesearch.core.codec

import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.DocumentDecoder.JsonError
import ai.nixiesearch.core.codec.FieldCodec.WireDecodingError
import ai.nixiesearch.core.search.DocumentGroup
import ai.nixiesearch.core.{Field, Logging}
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import io.circe.Decoder.Result
import io.circe.{ACursor, Decoder, DecodingFailure, Json}
import org.apache.lucene.document.Document as LuceneDocument
import org.apache.lucene.search.SortField

import scala.annotation.tailrec

trait FieldCodec[T <: Field, S <: FieldSchema[T], U] extends Logging {
  val FILTER_SUFFIX  = "$raw"
  val SORT_SUFFIX    = "$sort"
  val SUGGEST_SUFFIX = "$suggest"

  def writeLucene(field: T, spec: S, buffer: DocumentGroup): Unit
  def readLucene(name: String, spec: S, value: U): Either[WireDecodingError, T]
  def encodeJson(field: T): Json
  def makeDecoder(spec: S, fieldName: String): Decoder[Option[T]]

  def decodeJson(spec: S, name: String, reader: JsonReader): Either[JsonError, T] = Left(JsonError("error"))

}

object FieldCodec {
  case class WireDecodingError(msg: String) extends Exception(msg)
}

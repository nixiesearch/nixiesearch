package ai.nixiesearch.core.codec

import ai.nixiesearch.api.SearchRoute.SortPredicate.MissingValue
import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.core.codec.FieldCodec.WireDecodingError
import ai.nixiesearch.core.{Field, Logging}
import io.circe.Decoder.Result
import io.circe.{ACursor, Decoder, DecodingFailure, Json}
import org.apache.lucene.document.Document as LuceneDocument
import org.apache.lucene.search.SortField

import scala.annotation.tailrec

trait FieldCodec[T <: Field, S <: FieldSchema[T], U] extends Logging {
  def writeLucene(field: T, spec: S, buffer: LuceneDocument, embeddings: Map[String, Array[Float]]): Unit
  def readLucene(name: String, spec: S, value: U): Either[WireDecodingError, T]
  def encodeJson(field: T): Json
  
}

object FieldCodec {
  case class WireDecodingError(msg: String) extends Exception(msg)
}

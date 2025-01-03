package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.core.codec.FieldCodec.WireDecodingError
import ai.nixiesearch.core.{Field, Logging}
import io.circe.Decoder.Result
import io.circe.{ACursor, Decoder, DecodingFailure, Json}
import org.apache.lucene.document.Document as LuceneDocument

import scala.annotation.tailrec

trait FieldCodec[T <: Field, S <: FieldSchema[T], U] extends Logging {
  def writeLucene(field: T, spec: S, buffer: LuceneDocument, embeddings: Map[String, Array[Float]]): Unit
  def readLucene(spec: S, value: U): Either[WireDecodingError, T]
  def encodeJson(field: T): Json
  def decodeJson(schema: S, cursor: ACursor): Decoder.Result[Option[T]]

  @tailrec
  final protected def decodeRecursiveScalar[U](
      parts: List[String],
      schema: S,
      cursor: ACursor,
      as: ACursor => Decoder.Result[Option[U]],
      to: U => T
  ): Result[Option[T]] =
    parts match {
      case head :: tail =>
        decodeRecursiveScalar(tail, schema, cursor.downField(head), as, to)
      case Nil =>
        as(cursor) match {
          case Left(value) =>
            val value = cursor.focus
            Left(DecodingFailure(s"Field ${schema.name} should be a string, but got '$value'", cursor.history))
          case Right(Some(value)) => Right(Some(to(value)))
          case Right(None)        => Right(None)
        }
    }

}

object FieldCodec {
  case class WireDecodingError(msg: String) extends Exception(msg)
}

package ai.nixiesearch.core.codec

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.core.codec.FieldCodec.WireDecodingError
import ai.nixiesearch.core.{Field, Logging}
import org.apache.lucene.document.Document as LuceneDocument

trait FieldCodec[T <: Field, S <: FieldSchema[T], U] extends Logging {
  def write(field: T, spec: S, buffer: LuceneDocument, embeddings: Map[String, Array[Float]]): Unit

  def read(spec: S, value: U): Either[WireDecodingError, T]
}

object FieldCodec {
  case class WireDecodingError(msg: String) extends Exception(msg)
}

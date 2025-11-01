package ai.nixiesearch.core

import ai.nixiesearch.config.FieldSchema.{IdFieldSchema, IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Field.*
import com.github.plokhotnyuk.jsoniter_scala.core.*

import scala.collection.mutable.ArrayBuffer

object DocumentDecoder {
  case class JsonError(msg: String, err: Throwable = null) extends Throwable(msg, err)

  def codec(mapping: IndexMapping): JsonValueCodec[Document] = new JsonValueCodec[Document] {
    override def decodeValue(in: JsonReader, default: Document): Document = {
      if (in.isNextToken('{')) {
        val fields = ArrayBuffer.empty[Field]

        if (!in.isNextToken('}')) {
          in.rollbackToken()
          while ({
            val fieldName = in.readKeyAsString()
            mapping
              .fieldSchema(StringName(fieldName))
              .foreach(schema =>
                schema.codec.decodeJson(fieldName, in) match {
                  case Left(err)          => in.decodeError(err.msg)
                  case Right(Some(value)) => fields.addOne(value)
                  case Right(None)        => // skip
                }
              )

            in.isNextToken(',')
          }) ()

          if (!in.isCurrentToken('}')) {
            in.objectEndOrCommaError()
          }
        }

        if (fields.isEmpty) {
          throw JsonError("document cannot be empty")
        }

        Document(fields.toList)
      } else {
        in.decodeError("expected '{'")
      }
    }

    override def encodeValue(x: Document, out: JsonWriter): Unit =
      throw BackendError("jsoniter encoder not implemented")

    override def nullValue: Document = Document(Nil)
  }
}

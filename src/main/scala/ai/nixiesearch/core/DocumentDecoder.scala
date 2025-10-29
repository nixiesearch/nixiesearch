package ai.nixiesearch.core

import ai.nixiesearch.config.FieldSchema.{IdFieldSchema, IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.field.{IdField, IntField, TextField}
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

            mapping.fieldSchema(fieldName) match {
              case Some(s: IdFieldSchema) =>
                val field = IdField.decodeJson(s, fieldName, in)
                fields.addOne(field.toOption.get)
              case Some(s: TextFieldSchema) =>
                val field = TextField.decodeJson(s, fieldName, in)
                fields.addOne(field.toOption.get)

              case Some(_: IntFieldSchema) =>
                val value = in.readInt()
                fields += IntField(fieldName, value)

              case _ =>
                // Skip unknown fields
                in.skip()
            }

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

    override def encodeValue(x: Document, out: JsonWriter): Unit = ???

    override def nullValue: Document = Document(Nil)
  }
}

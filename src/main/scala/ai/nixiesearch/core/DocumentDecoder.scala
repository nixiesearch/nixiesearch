package ai.nixiesearch.core

import ai.nixiesearch.config.FieldSchema.{IdFieldSchema, IntFieldSchema, TextFieldSchema}
import ai.nixiesearch.config.mapping.FieldName
import ai.nixiesearch.config.mapping.FieldName.StringName
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Error.BackendError
import ai.nixiesearch.core.Field.*
import com.github.plokhotnyuk.jsoniter_scala.core.*

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object DocumentDecoder {
  case class JsonError(msg: String, err: Throwable = null) extends Throwable(msg, err)

  def codec(mapping: IndexMapping): JsonValueCodec[Document] = new JsonValueCodec[Document] {

    // Decode single-level nested object by flattening with dot-notation prefix
    def decodeNestedObject(prefix: String, in: JsonReader): List[Field] = {
      val fields = ArrayBuffer.empty[Field]

      if (in.isNextToken('{')) {
        if (!in.isNextToken('}')) {
          in.rollbackToken()
          while ({
            val fieldName = in.readKeyAsString()
            val fullFieldName = s"$prefix.$fieldName"

            // Try to find this nested field in the mapping
            // Use FieldName.parse to properly handle dot-notation and wildcards
            FieldName.parse(fullFieldName) match {
              case Right(parsedFieldName) =>
                mapping.fieldSchema(parsedFieldName) match {
                  case Some(schema) =>
                    schema.codec.decodeJson(fullFieldName, in) match {
                      case Left(err)          => in.decodeError(err.msg)
                      case Right(Some(value)) => fields.addOne(value)
                      case Right(None)        => // skip
                    }
                  case None =>
                    // Field not in mapping, skip the value
                    in.skip()
                }
              case Left(err) =>
                // Cannot parse field name - this is an error
                in.decodeError(err.getMessage)
            }

            in.isNextToken(',')
          }) ()

          if (!in.isCurrentToken('}')) {
            in.objectEndOrCommaError()
          }
        }
      } else {
        in.decodeError("nested object should start with '{'")
      }

      fields.toList
    }

    // Decode array of single-level nested objects by collecting values into repeated fields
    def decodeNestedArray(prefix: String, in: JsonReader): List[Field] = {
      val stringValues = mutable.Map[String, mutable.ArrayBuffer[String]]()
      val intValues = mutable.Map[String, mutable.ArrayBuffer[Int]]()
      val longValues = mutable.Map[String, mutable.ArrayBuffer[Long]]()
      val floatValues = mutable.Map[String, mutable.ArrayBuffer[Float]]()
      val doubleValues = mutable.Map[String, mutable.ArrayBuffer[Double]]()

      if (in.isNextToken('[')) {
        if (!in.isNextToken(']')) {
          in.rollbackToken()
          while ({
            val token = in.nextToken()
            token match {
              case '{' =>
                // Process nested object in array
                in.rollbackToken()
                if (in.isNextToken('{')) {
                  if (!in.isNextToken('}')) {
                    in.rollbackToken()
                    while ({
                      val fieldName = in.readKeyAsString()
                      val fullFieldName = s"$prefix.$fieldName"

                      // Read value based on next token
                      val nextToken = in.nextToken()
                      in.rollbackToken()

                      nextToken match {
                        case '"' =>
                          val value = in.readString(null)
                          stringValues.getOrElseUpdate(fullFieldName, mutable.ArrayBuffer.empty) += value
                        case 't' | 'f' =>
                          // Skip boolean values as there's no BooleanListField
                          in.skip()
                        case 'n' =>
                          in.readNullOrError(null, "null")
                        case _ =>
                          // Read as number
                          val value = in.readDouble()
                          if (value.isWhole && value >= Int.MinValue && value <= Int.MaxValue) {
                            intValues.getOrElseUpdate(fullFieldName, mutable.ArrayBuffer.empty) += value.toInt
                          } else if (value.isWhole && value >= Long.MinValue && value <= Long.MaxValue) {
                            longValues.getOrElseUpdate(fullFieldName, mutable.ArrayBuffer.empty) += value.toLong
                          } else {
                            doubleValues.getOrElseUpdate(fullFieldName, mutable.ArrayBuffer.empty) += value
                          }
                      }

                      in.isNextToken(',')
                    }) ()

                    if (!in.isCurrentToken('}')) {
                      in.objectEndOrCommaError()
                    }
                  }
                }
              case _ =>
                in.rollbackToken()
                in.skip() // Skip non-object array elements
            }

            in.isNextToken(',')
          }) ()

          if (!in.isCurrentToken(']')) {
            in.arrayEndOrCommaError()
          }
        }
      } else {
        in.decodeError("array should start with '['")
      }

      // Convert accumulated values to fields
      val fields = ArrayBuffer.empty[Field]
      stringValues.foreach { case (name, values) => fields.addOne(TextListField(name, values.toList)) }
      intValues.foreach { case (name, values) => fields.addOne(IntListField(name, values.toList)) }
      longValues.foreach { case (name, values) => fields.addOne(LongListField(name, values.toList)) }
      floatValues.foreach { case (name, values) => fields.addOne(FloatListField(name, values.toList)) }
      doubleValues.foreach { case (name, values) => fields.addOne(DoubleListField(name, values.toList)) }

      fields.toList
    }

    override def decodeValue(in: JsonReader, default: Document): Document = {
      if (in.isNextToken('{')) {
        val fields = ArrayBuffer.empty[Field]

        if (!in.isNextToken('}')) {
          in.rollbackToken()
          while ({
            val fieldName = in.readKeyAsString()

            // Try to find field in mapping first
            mapping.fieldSchema(StringName(fieldName)) match {
              case Some(schema) =>
                // Found in mapping, use the schema's codec
                schema.codec.decodeJson(fieldName, in) match {
                  case Left(err)          => in.decodeError(err.msg)
                  case Right(Some(value)) => fields.addOne(value)
                  case Right(None)        => // skip
                }
              case None =>
                // Not in mapping, check if it's a nested structure to flatten
                val token = in.nextToken()
                in.rollbackToken()
                token match {
                  case '{' => fields.addAll(decodeNestedObject(fieldName, in))
                  case '[' => fields.addAll(decodeNestedArray(fieldName, in))
                  case _   => in.skip() // Skip unknown primitive value
                }
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
        in.decodeError("document should start with '{'")
      }
    }

    override def encodeValue(x: Document, out: JsonWriter): Unit =
      throw BackendError("jsoniter encoder not implemented")

    override def nullValue: Document = Document(Nil)
  }
}

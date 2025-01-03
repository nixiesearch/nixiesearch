package ai.nixiesearch.core

import ai.nixiesearch.config.FieldSchema
import ai.nixiesearch.config.FieldSchema.*
import ai.nixiesearch.core.Field.*
import io.circe.Decoder.Result
import io.circe.{ACursor, Decoder, DecodingFailure, FailedCursor, HCursor, Json}

import java.util.UUID
import scala.annotation.tailrec

trait FieldJson[T <: Field, F <: FieldSchema[T]] {
  def encode(field: T): Json
  def decode(schema: F, cursor: ACursor): Decoder.Result[Option[T]]

  @tailrec
  final protected def decodeRecursiveScalar[U](
      parts: List[String],
      schema: F,
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

object FieldJson {
  object TextFieldJson extends FieldJson[TextField, TextFieldSchema] {
    override def encode(field: TextField): Json = Json.fromString(field.value)
    override def decode(schema: TextFieldSchema, cursor: ACursor): Result[Option[TextField]] = {
      val parts = schema.name.split('.').toList
      if (schema.name == "_id") {
        decodeRecursiveScalar[String](parts, schema, cursor, _.as[Option[String]], TextField(schema.name, _)) match {
          case Left(_) | Right(None) =>
            decodeRecursiveScalar[Long](
              parts,
              schema,
              cursor,
              _.as[Option[Long]],
              (x: Long) => TextField(schema.name, x.toString)
            ) match {
              case Left(err)    => Left(err)
              case Right(None)  => Right(Some(TextField("_id", UUID.randomUUID().toString)))
              case Right(value) => Right(value)
            }
          case Right(value) => Right(value)
        }
      } else {
        decodeRecursiveScalar[String](parts, schema, cursor, _.as[Option[String]], TextField(schema.name, _))
      }

    }

  }

  object TextListFieldJson extends FieldJson[TextListField, TextListFieldSchema] {
    override def encode(field: TextListField): Json = Json.fromValues(field.value.map(Json.fromString))
    override def decode(schema: TextListFieldSchema, cursor: ACursor): Result[Option[TextListField]] = {
      val parts = schema.name.split('.').toList
      decodeRecursive(parts, schema, cursor.focus.get, cursor, Nil) match {
        case Right(Nil)      => Right(None)
        case Right(nonEmpty) => Right(Some(TextListField(schema.name, nonEmpty)))
        case Left(err)       => Left(err)
      }
    }

    private def decodeRecursive(
        parts: List[String],
        schema: TextListFieldSchema,
        json: Json,
        cursor: ACursor,
        acc: List[String]
    ): Result[List[String]] = parts match {
      case head :: tail =>
        if (json.isObject) {
          json.asObject.flatMap(_.apply(head)) match {
            case Some(value) => decodeRecursive(tail, schema, value, cursor, acc)
            case None        => Right(Nil)
          }
        } else if (json.isArray) {
          json.asArray.toList.flatten.foldLeft[Decoder.Result[List[String]]](Right(Nil)) {
            case (Right(list), next) =>
              decodeRecursive(head :: tail, schema, next, cursor, list) match {
                case Left(err)    => Left(err)
                case Right(value) => Right(list ++ value)
              }
            case (Left(err), _) => Left(err)
          }
        } else {
          Left(
            DecodingFailure(
              s"for text[] field ${schema.name} we expect root obj/array json value, but got $json",
              cursor.history
            )
          )
        }
      case Nil =>
        if (json.isString) {
          Right(json.asString.toList)
        } else if (json.isArray) {
          json.as[List[String]]
        } else {
          Left(
            DecodingFailure(
              s"for text[] field ${schema.name} we expect string/string[] json value, but got $json",
              cursor.history
            )
          )
        }
    }
  }

  object IntFieldJson extends FieldJson[IntField, IntFieldSchema] {
    override def encode(field: IntField): Json = Json.fromInt(field.value)

    override def decode(schema: IntFieldSchema, cursor: ACursor): Result[Option[IntField]] = {
      val parts = schema.name.split('.').toList
      decodeRecursiveScalar[Int](parts, schema, cursor, _.as[Option[Int]], IntField(schema.name, _))
    }
  }

  object FloatFieldJson extends FieldJson[FloatField, FloatFieldSchema] {
    override def encode(field: FloatField): Json = Json.fromFloatOrNull(field.value)

    override def decode(schema: FloatFieldSchema, cursor: ACursor): Result[Option[FloatField]] = {
      val parts = schema.name.split('.').toList
      decodeRecursiveScalar[Float](parts, schema, cursor, _.as[Option[Float]], FloatField(schema.name, _))
    }
  }

  object LongFieldJson extends FieldJson[LongField, LongFieldSchema] {
    override def encode(field: LongField): Json = Json.fromLong(field.value)

    override def decode(schema: LongFieldSchema, cursor: ACursor): Result[Option[LongField]] = {
      val parts = schema.name.split('.').toList
      decodeRecursiveScalar[Long](parts, schema, cursor, _.as[Option[Long]], LongField(schema.name, _))
    }
  }

  object DoubleFieldJson extends FieldJson[DoubleField, DoubleFieldSchema] {
    override def encode(field: DoubleField): Json = Json.fromDoubleOrNull(field.value)

    override def decode(schema: DoubleFieldSchema, cursor: ACursor): Result[Option[DoubleField]] = {
      val parts = schema.name.split('.').toList
      decodeRecursiveScalar[Double](parts, schema, cursor, _.as[Option[Double]], DoubleField(schema.name, _))
    }
  }

  object BooleanFieldJson extends FieldJson[BooleanField, BooleanFieldSchema] {
    override def encode(field: BooleanField): Json = Json.fromBoolean(field.value)

    override def decode(schema: BooleanFieldSchema, cursor: ACursor): Result[Option[BooleanField]] = {
      val parts = schema.name.split('.').toList
      decodeRecursiveScalar[Boolean](parts, schema, cursor, _.as[Option[Boolean]], BooleanField(schema.name, _))
    }
  }

  object GeopointFieldJson extends FieldJson[GeopointField, GeopointFieldSchema] {
    override def encode(field: GeopointField): Json =
      Json.obj("lat" -> Json.fromDoubleOrNull(field.lat), "lon" -> Json.fromDoubleOrNull(field.lon))

    override def decode(schema: GeopointFieldSchema, cursor: ACursor): Result[Option[GeopointField]] = for {
      latOption <- cursor.downField(schema.name).downField("lat").as[Option[Double]]
      lonOption <- cursor.downField(schema.name).downField("lon").as[Option[Double]]
      field <- (latOption, lonOption) match {
        case (Some(lat), Some(lon)) => Right(Some(GeopointField(schema.name, lat, lon)))
        case (None, None)           => Right(None)
        case (errLat, errLon) =>
          Left(DecodingFailure(s"cannot decode geopoint field '${schema.name}' from ${cursor.focus}", cursor.history))
      }
    } yield {
      field
    }
  }
}

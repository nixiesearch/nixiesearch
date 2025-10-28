package ai.nixiesearch.config.mapping

import ai.nixiesearch.core.Error.UserError
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

sealed trait FieldName {
  def name: String
  def matches(field: String): Boolean
}

object FieldName {
  case class StringName(name: String) extends FieldName {
    override def matches(field: String): Boolean = field == name
  }

  case class NestedName(name: String, head: String, tail: String) extends FieldName {
    override def matches(field: String): Boolean = field == name
  }

  case class WildcardName(name: String, prefix: String, suffix: String) extends FieldName {
    override def matches(field: String): Boolean = field.startsWith(prefix) && field.endsWith(suffix)
  }

  def unsafe(value: String) = parse(value).toOption.get

  def parse(field: String): Either[Throwable, FieldName] = {
    val placeholderPos = field.indexOf('*')
    val dotPos         = field.indexOf('.')
    if ((placeholderPos == -1) && (dotPos == -1)) {
      Right(StringName(field))
    } else if (dotPos >= 0) {
      if (field.indexOf('.', dotPos + 1) == -1) {
        Right(NestedName(field, field.substring(0, dotPos), field.substring(dotPos + 1)))
      } else {
        Left(UserError(s"cannot parse nested field name '$field': more than one '.' placeholder found"))
      }
    } else if (placeholderPos >= 0) {
      if (field.indexOf('*', placeholderPos + 1) == -1) {
        Right(WildcardName(field, field.substring(0, placeholderPos), field.substring(placeholderPos + 1)))
      } else {
        Left(UserError(s"cannot parse wildcard field name '$field': more than one '*' placeholder found"))
      }
    } else {
      Left(UserError(s"we support only nested OR wildcard fields, not both: got field '$field'"))
    }
  }

  given fieldNameEncoder: Encoder[FieldName]       = Encoder.encodeString.contramap(_.name)
  given fieldNameDecoder: Decoder[FieldName]       = Decoder.decodeString.emapTry { parse(_).toTry }
  given fieldNameKeyDecoder: KeyDecoder[FieldName] = KeyDecoder.instance(parse(_).toOption)
  given fieldNameKeyEncoder: KeyEncoder[FieldName] = KeyEncoder.instance(_.name)

//  given fieldStringConversion: Conversion[String, FieldName] with {
//    override def apply(x: String): FieldName = FieldName.parse(x) match {
//      case Left(value)  => ???
//      case Right(value) => value
//    }
//  }
}

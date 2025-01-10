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

  case class WildcardName(name: String, prefix: String, suffix: String) extends FieldName {
    override def matches(field: String): Boolean = field.startsWith(prefix) && field.endsWith(suffix)
  }

  def unsafe(value: String) = parse(value).toOption.get

  def parse(field: String): Either[Throwable, FieldName] = {
    val pos = field.indexOf('*')
    if (pos == -1) {
      Right(StringName(field))
    } else {
      if (field.indexOf('*', pos + 1) == -1) {
        Right(WildcardName(field, field.substring(0, pos), field.substring(pos + 1)))
      } else {
        Left(UserError(s"cannot parse wildcard field name '$field': more than one '*' placeholder found"))
      }
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

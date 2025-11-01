package ai.nixiesearch.config.mapping

import ai.nixiesearch.core.Error.UserError
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

sealed trait FieldName {
  def name: String
  def matches(field: FieldName): Boolean
}

object FieldName {
  case class StringName(name: String) extends FieldName {
    override def matches(field: FieldName): Boolean = field match {
      case StringName(xname)                   => name == xname
      case NestedName(xname, parent, child)    => name == xname
      case WildcardName(xname, prefix, suffix) => name.startsWith(prefix) && name.endsWith(suffix)
    }
  }

  case class NestedName(name: String, parent: String, child: String) extends FieldName {
    override def matches(field: FieldName): Boolean = field match {
      case StringName(xname)                   => name == xname
      case NestedName(xname, parent, child)    => name == xname
      case WildcardName(xname, prefix, suffix) =>
        name.startsWith(prefix) && name.endsWith(suffix) // maybe it could be better?
    }
  }

  case class WildcardName(name: String, prefix: String, suffix: String) extends FieldName {
    override def matches(field: FieldName): Boolean = field match {
      case StringName(xname)                   => xname.startsWith(prefix) && name.endsWith(suffix)
      case NestedName(xname, parent, child)    => xname.startsWith(prefix) && name.endsWith(suffix)
      case WildcardName(xname, prefix, suffix) => (prefix == prefix) && (suffix == suffix)
    }
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

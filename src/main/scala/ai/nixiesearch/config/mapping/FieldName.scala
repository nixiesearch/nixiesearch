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
      case NestedWildcardName(xname, xparent, xchild, xprefix, xsuffix) =>
        name.startsWith(xprefix) && name.endsWith(xsuffix)
    }
  }

  case class NestedName(name: String, parent: String, child: String) extends FieldName {
    override def matches(field: FieldName): Boolean = field match {
      case StringName(xname)                   => name == xname
      case NestedName(xname, parent, child)    => name == xname
      case WildcardName(xname, prefix, suffix) =>
        name.startsWith(prefix) && name.endsWith(suffix)
      case NestedWildcardName(xname, xparent, xchild, xprefix, xsuffix) =>
        name.startsWith(xprefix) && name.endsWith(xsuffix)
    }
  }

  case class WildcardName(name: String, prefix: String, suffix: String) extends FieldName {
    override def matches(field: FieldName): Boolean = field match {
      case StringName(xname)                   => xname.startsWith(prefix) && xname.endsWith(suffix)
      case NestedName(xname, parent, child)    => xname.startsWith(prefix) && xname.endsWith(suffix)
      case WildcardName(xname, xprefix, xsuffix) => (this.prefix == xprefix) && (this.suffix == xsuffix)
      case NestedWildcardName(xname, xparent, xchild, xprefix, xsuffix) =>
        xprefix.startsWith(this.prefix) && xsuffix.endsWith(this.suffix)
    }
  }

  case class NestedWildcardName(name: String, parent: String, child: String, prefix: String, suffix: String) extends FieldName {
    override def matches(field: FieldName): Boolean = field match {
      case StringName(xname) =>
        // Match against full pattern: prefix*suffix (where prefix includes parent)
        xname.startsWith(prefix) && xname.endsWith(suffix)
      case NestedName(xname, xparent, xchild) =>
        // Match full name against prefix*suffix pattern
        xname.startsWith(prefix) && xname.endsWith(suffix)
      case WildcardName(xname, xprefix, xsuffix) =>
        // Check if wildcard pattern overlaps
        xprefix.startsWith(prefix) && xsuffix.endsWith(suffix)
      case NestedWildcardName(xname, xparent, xchild, xprefix, xsuffix) =>
        // All components must match
        (prefix == xprefix) && (suffix == xsuffix)
    }
  }

  def unsafe(value: String) = parse(value).toOption.get

  def parse(field: String): Either[Throwable, FieldName] = {
    val placeholderPos = field.indexOf('*')
    val dotPos         = field.indexOf('.')

    // Check for both . and * - NestedWildcardName
    if ((dotPos >= 0) && (placeholderPos >= 0)) {
      // Validate only one . and one *
      if (field.indexOf('.', dotPos + 1) != -1) {
        Left(UserError(s"cannot parse nested wildcard field name '$field': more than one '.' found"))
      } else if (field.indexOf('*', placeholderPos + 1) != -1) {
        Left(UserError(s"cannot parse nested wildcard field name '$field': more than one '*' found"))
      } else if (placeholderPos < dotPos) {
        Left(UserError(s"cannot parse nested wildcard field name '$field': '*' must be after '.'"))
      } else {
        // Extract components: parent.childPrefix*childSuffix
        val parent = field.substring(0, dotPos)
        val child = field.substring(dotPos + 1)
        val prefix = field.substring(0, placeholderPos)  // Full prefix including parent and dot
        val suffix = field.substring(placeholderPos + 1)  // Part after *
        Right(NestedWildcardName(field, parent, child, prefix, suffix))
      }
    } else if ((placeholderPos == -1) && (dotPos == -1)) {
      // Simple field
      Right(StringName(field))
    } else if (dotPos >= 0) {
      // Nested field without wildcard
      if (field.indexOf('.', dotPos + 1) == -1) {
        Right(NestedName(field, field.substring(0, dotPos), field.substring(dotPos + 1)))
      } else {
        Left(UserError(s"cannot parse nested field name '$field': more than one '.' placeholder found"))
      }
    } else if (placeholderPos >= 0) {
      // Wildcard field without nesting
      if (field.indexOf('*', placeholderPos + 1) == -1) {
        Right(WildcardName(field, field.substring(0, placeholderPos), field.substring(placeholderPos + 1)))
      } else {
        Left(UserError(s"cannot parse wildcard field name '$field': more than one '*' placeholder found"))
      }
    } else {
      Left(UserError(s"unexpected field name format: '$field'"))
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

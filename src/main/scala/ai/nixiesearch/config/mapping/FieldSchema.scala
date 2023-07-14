package ai.nixiesearch.config

import ai.nixiesearch.config.Language.English
import ai.nixiesearch.config.SearchType.NoSearch
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.{IntField, TextField, TextListField}
import io.circe.{Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.*
import io.circe.Json
import io.circe.JsonObject

sealed trait FieldSchema[T <: Field] {
  def name: String
  def store: Boolean
  def sort: Boolean
  def facet: Boolean
  def filter: Boolean
}

object FieldSchema {
  case class TextFieldSchema(
      name: String,
      search: SearchType = NoSearch,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[TextField]

  case class TextListFieldSchema(
      name: String,
      search: SearchType = NoSearch,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[TextListField]

  case class IntFieldSchema(
      name: String,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[IntField]

  object yaml {
    def textFieldSchemaDecoder(name: String): Decoder[TextFieldSchema] = Decoder.instance(c =>
      for {
        search <- c.downField("search").as[Option[SearchType]].map(_.getOrElse(NoSearch))
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        TextFieldSchema(name, search, store, sort, facet, filter)
      }
    )
    def textListFieldSchemaDecoder(name: String): Decoder[TextListFieldSchema] = Decoder.instance(c =>
      for {
        search <- c.downField("search").as[Option[SearchType]].map(_.getOrElse(NoSearch))
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        TextListFieldSchema(name, search, store, sort, facet, filter)
      }
    )
    def intFieldSchemaDecoder(name: String): Decoder[IntFieldSchema] = Decoder.instance(c =>
      for {
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        IntFieldSchema(name, store, sort, facet, filter)
      }
    )

    def fieldSchemaDecoder(name: String): Decoder[FieldSchema[_ <: Field]] = Decoder.instance(c =>
      c.downField("type").as[String] match {
        case Left(value)                  => Left(DecodingFailure(s"Cannot decode field type: $value", c.history))
        case Right("text" | "string")     => textFieldSchemaDecoder(name).tryDecode(c)
        case Right("text[]" | "string[]") => textListFieldSchemaDecoder(name).tryDecode(c)
        case Right("int")                 => intFieldSchemaDecoder(name).tryDecode(c)
        case Right(other) =>
          Left(DecodingFailure(s"Field type '$other' is not supported. Maybe try 'text'?", c.history))
      }
    )

  }

  object json {
    implicit val textFieldSchemaEncoder: Encoder[TextFieldSchema] = deriveEncoder
    implicit val textFieldSchemaDecoder: Decoder[TextFieldSchema] = deriveDecoder

    implicit val textListFieldSchemaEncoder: Encoder[TextListFieldSchema] = deriveEncoder
    implicit val textListFieldSchemaDecoder: Decoder[TextListFieldSchema] = deriveDecoder

    implicit val intFieldSchemaDecoder: Decoder[IntFieldSchema] = deriveDecoder
    implicit val intFieldSchemaEncoder: Encoder[IntFieldSchema] = deriveEncoder

    implicit val fieldSchemaEncoder: Encoder[FieldSchema[_ <: Field]] = Encoder.instance {
      case f: IntFieldSchema      => intFieldSchemaEncoder.apply(f).deepMerge(withType("int"))
      case f: TextFieldSchema     => textFieldSchemaEncoder.apply(f).deepMerge(withType("text"))
      case f: TextListFieldSchema => textListFieldSchemaEncoder.apply(f).deepMerge(withType("text[]"))
    }

    implicit val fieldSchemaDecoder: Decoder[FieldSchema[_ <: Field]] = Decoder.instance(c =>
      c.downField("type").as[String] match {
        case Right("int")    => intFieldSchemaDecoder.tryDecode(c)
        case Right("text")   => textFieldSchemaDecoder.tryDecode(c)
        case Right("text[]") => textListFieldSchemaDecoder.tryDecode(c)
        case Right(other)    => Left(DecodingFailure(s"field type '$other' is not supported", c.history))
        case Left(err)       => Left(err)
      }
    )

    def withType(tpe: String): Json = Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString(tpe))))
  }

}

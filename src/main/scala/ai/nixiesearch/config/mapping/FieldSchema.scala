package ai.nixiesearch.config

import ai.nixiesearch.config.mapping.SearchType.{LexicalSearch, NoSearch}
import ai.nixiesearch.config.mapping.SearchType
import ai.nixiesearch.config.mapping.SearchType.yaml.searchTypeDecoder
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.{FloatField, IntField, TextField, TextListField}
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
  object TextLikeFieldSchema {
    def unapply(f: FieldSchema[_ <: Field]): Option[(String, SearchType, Boolean, Boolean, Boolean, Boolean)] =
      f match {
        case TextFieldSchema(name, search, store, sort, facet, filter) =>
          Some((name, search, store, sort, facet, filter))
        case TextListFieldSchema(name, search, store, sort, facet, filter) =>
          Some((name, search, store, sort, facet, filter))
        case _ => None
      }
  }

  case class TextFieldSchema(
      name: String,
      search: SearchType = NoSearch,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[TextField]

  object TextFieldSchema {
    def dynamicDefault(name: String) = new TextFieldSchema(
      name = name,
      search = LexicalSearch(),
      sort = true,
      facet = true,
      filter = true
    )
  }

  case class TextListFieldSchema(
      name: String,
      search: SearchType = NoSearch,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[TextListField]

  object TextListFieldSchema {
    def dynamicDefault(name: String) = new TextListFieldSchema(
      name = name,
      search = LexicalSearch(),
      sort = true,
      facet = true,
      filter = true
    )
  }
  case class IntFieldSchema(
      name: String,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[IntField]

  case class FloatFieldSchema(
      name: String,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[FloatField]

  object FloatFieldSchema {
    def dynamicDefault(name: String) = new FloatFieldSchema(
      name = name,
      sort = true,
      facet = true,
      filter = true
    )
  }

  object IntFieldSchema {
    def dynamicDefault(name: String) = new IntFieldSchema(
      name = name,
      sort = true,
      facet = true,
      filter = true
    )
  }

  object yaml {
    import SearchType.yaml.*

    def textFieldSchemaDecoder(name: String): Decoder[TextFieldSchema] = Decoder.instance(c =>
      for {
        search <- c
          .downField("search")
          .as[Option[SearchType]](Decoder.decodeOption(searchTypeDecoder))
          .map(_.getOrElse(NoSearch))
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

    def floatFieldSchemaDecoder(name: String): Decoder[FloatFieldSchema] = Decoder.instance(c =>
      for {
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        FloatFieldSchema(name, store, sort, facet, filter)
      }
    )

    def fieldSchemaDecoder(name: String): Decoder[FieldSchema[_ <: Field]] = Decoder.instance(c =>
      c.downField("type").as[String] match {
        case Left(value)                  => Left(DecodingFailure(s"Cannot decode field type: $value", c.history))
        case Right("text" | "string")     => textFieldSchemaDecoder(name).tryDecode(c)
        case Right("text[]" | "string[]") => textListFieldSchemaDecoder(name).tryDecode(c)
        case Right("int")                 => intFieldSchemaDecoder(name).tryDecode(c)
        case Right("float")               => floatFieldSchemaDecoder(name).tryDecode(c)
        case Right(other) =>
          Left(DecodingFailure(s"Field type '$other' is not supported. Maybe try 'text'?", c.history))
      }
    )

  }

  object json {
    import SearchType.json.*

    implicit val textFieldSchemaEncoder: Encoder[TextFieldSchema] = deriveEncoder
    implicit val textFieldSchemaDecoder: Decoder[TextFieldSchema] = deriveDecoder

    implicit val textListFieldSchemaEncoder: Encoder[TextListFieldSchema] = deriveEncoder
    implicit val textListFieldSchemaDecoder: Decoder[TextListFieldSchema] = deriveDecoder

    implicit val intFieldSchemaDecoder: Decoder[IntFieldSchema] = deriveDecoder
    implicit val intFieldSchemaEncoder: Encoder[IntFieldSchema] = deriveEncoder

    implicit val floatFieldSchemaDecoder: Decoder[FloatFieldSchema] = deriveDecoder
    implicit val floatFieldSchemaEncoder: Encoder[FloatFieldSchema] = deriveEncoder

    implicit val fieldSchemaEncoder: Encoder[FieldSchema[_ <: Field]] = Encoder.instance {
      case f: IntFieldSchema      => intFieldSchemaEncoder.apply(f).deepMerge(withType("int"))
      case f: FloatFieldSchema    => floatFieldSchemaEncoder.apply(f).deepMerge(withType("float"))
      case f: TextFieldSchema     => textFieldSchemaEncoder.apply(f).deepMerge(withType("text"))
      case f: TextListFieldSchema => textListFieldSchemaEncoder.apply(f).deepMerge(withType("text[]"))
    }

    implicit val fieldSchemaDecoder: Decoder[FieldSchema[_ <: Field]] = Decoder.instance(c =>
      c.downField("type").as[String] match {
        case Right("int")    => intFieldSchemaDecoder.tryDecode(c)
        case Right("float")  => floatFieldSchemaDecoder.tryDecode(c)
        case Right("text")   => textFieldSchemaDecoder.tryDecode(c)
        case Right("text[]") => textListFieldSchemaDecoder.tryDecode(c)
        case Right(other)    => Left(DecodingFailure(s"field type '$other' is not supported", c.history))
        case Left(err)       => Left(err)
      }
    )

    def withType(tpe: String): Json = Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString(tpe))))
  }

}

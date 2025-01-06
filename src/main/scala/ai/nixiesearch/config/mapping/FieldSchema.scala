package ai.nixiesearch.config

import ai.nixiesearch.config.mapping.SearchType.NoSearch
import ai.nixiesearch.config.mapping.{Language, SearchType, SuggestSchema}
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.TextLikeField
import ai.nixiesearch.core.field.*
import io.circe.{Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.*
import io.circe.Json
import io.circe.JsonObject

import language.experimental.namedTuples
import scala.NamedTuple.NamedTuple

sealed trait FieldSchema[T <: Field] {
  def name: String
  def store: Boolean
  def sort: Boolean
  def facet: Boolean
  def filter: Boolean
}

object FieldSchema {
  sealed trait TextLikeFieldSchema[T <: TextLikeField] extends FieldSchema[T] {
    def search: SearchType
    def language: Language
    def suggest: Option[SuggestSchema]
  }

  object TextLikeFieldSchema {
    def unapply(
        f: TextLikeFieldSchema[? <: Field]
    ): Option[
      NamedTuple[("name", "search", "language", "suggest"), (String, SearchType, Language, Option[SuggestSchema])]
    ] = {
      Some(
        NamedTuple[("name", "search", "language", "suggest"), (String, SearchType, Language, Option[SuggestSchema])](
          (f.name, f.search, f.language, f.suggest)
        )
      )
    }

  }

  case class TextFieldSchema(
      name: String,
      search: SearchType = NoSearch,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false,
      language: Language = Language.Generic,
      suggest: Option[SuggestSchema] = None
  ) extends TextLikeFieldSchema[TextField]
      with FieldSchema[TextField]

  case class TextListFieldSchema(
      name: String,
      search: SearchType = NoSearch,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false,
      language: Language = Language.Generic,
      suggest: Option[SuggestSchema] = None
  ) extends TextLikeFieldSchema[TextListField]
      with FieldSchema[TextListField]

  case class IntFieldSchema(
      name: String,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[IntField]

  case class LongFieldSchema(
      name: String,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[LongField]

  case class FloatFieldSchema(
      name: String,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[FloatField]

  case class DoubleFieldSchema(
      name: String,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[DoubleField]

  case class BooleanFieldSchema(
      name: String,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[BooleanField]

  case class GeopointFieldSchema(
      name: String,
      store: Boolean = true,
      filter: Boolean = false
  ) extends FieldSchema[GeopointField] {
    def sort  = false
    def facet = false
  }

  case class DateFieldSchema(
      name: String,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[DateField] {
    def asInt = IntFieldSchema(name, store, sort, facet, filter)
  }

  case class DateTimeFieldSchema(
      name: String,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[DateTimeField] {
    def asLong = LongFieldSchema(name, store, sort, facet, filter)
  }

  object yaml {
    import SearchType.yaml.given
    import SuggestSchema.yaml.given

    def textFieldSchemaDecoder(name: String): Decoder[TextFieldSchema] = Decoder.instance(c =>
      for {
        search <- c
          .downField("search")
          .as[Option[SearchType]](Decoder.decodeOption(searchTypeDecoder))
          .map(_.getOrElse(NoSearch))
        store    <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort     <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet    <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter   <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
        language <- c.downField("language").as[Option[Language]].map(_.getOrElse(Language.Generic))
        suggest <- c
          .downField("suggest")
          .as[Option[SuggestSchema]]
          .orElse(c.downField("suggest").as[Option[Boolean]].flatMap {
            case Some(true)  => Right(Some(SuggestSchema()))
            case Some(false) => Right(None)
            case None        => Right(None)
          })
      } yield {
        TextFieldSchema(name, search, store, sort, facet, filter, language, suggest)
      }
    )
    def textListFieldSchemaDecoder(name: String): Decoder[TextListFieldSchema] = Decoder.instance(c =>
      for {
        search   <- c.downField("search").as[Option[SearchType]].map(_.getOrElse(NoSearch))
        store    <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort     <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet    <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter   <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
        language <- c.downField("language").as[Option[Language]].map(_.getOrElse(Language.Generic))

        suggest <- c
          .downField("suggest")
          .as[Option[SuggestSchema]]
          .orElse(c.downField("suggest").as[Option[Boolean]].flatMap {
            case Some(true)  => Right(Some(SuggestSchema()))
            case Some(false) => Right(None)
            case None        => Right(None)
          })
      } yield {
        TextListFieldSchema(name, search, store, sort, facet, filter, language, suggest)
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

    def longFieldSchemaDecoder(name: String): Decoder[LongFieldSchema] = Decoder.instance(c =>
      for {
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        LongFieldSchema(name, store, sort, facet, filter)
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

    def doubleFieldSchemaDecoder(name: String): Decoder[DoubleFieldSchema] = Decoder.instance(c =>
      for {
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        DoubleFieldSchema(name, store, sort, facet, filter)
      }
    )

    def booleanFieldSchemaDecoder(name: String): Decoder[BooleanFieldSchema] = Decoder.instance(c =>
      for {
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        BooleanFieldSchema(name, store, sort, facet, filter)
      }
    )
    def dateFieldSchemaDecoder(name: String): Decoder[DateFieldSchema] = Decoder.instance(c =>
      for {
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        DateFieldSchema(name, store, sort, facet, filter)
      }
    )
    def dateTimeFieldSchemaDecoder(name: String): Decoder[DateTimeFieldSchema] = Decoder.instance(c =>
      for {
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        DateTimeFieldSchema(name, store, sort, facet, filter)
      }
    )

    def geopointFieldSchemaDecoder(name: String): Decoder[GeopointFieldSchema] = Decoder.instance(c =>
      for {
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        GeopointFieldSchema(name, store, filter)
      }
    )

    def fieldSchemaDecoder(name: String): Decoder[FieldSchema[? <: Field]] = Decoder.instance(c =>
      c.downField("type").as[String] match {
        case Left(value)                  => Left(DecodingFailure(s"Cannot decode field '$name': $value", c.history))
        case Right("text" | "string")     => textFieldSchemaDecoder(name).tryDecode(c)
        case Right("text[]" | "string[]") => textListFieldSchemaDecoder(name).tryDecode(c)
        case Right("int")                 => intFieldSchemaDecoder(name).tryDecode(c)
        case Right("long")                => longFieldSchemaDecoder(name).tryDecode(c)
        case Right("float")               => floatFieldSchemaDecoder(name).tryDecode(c)
        case Right("double")              => doubleFieldSchemaDecoder(name).tryDecode(c)
        case Right("bool")                => booleanFieldSchemaDecoder(name).tryDecode(c)
        case Right("geopoint")            => geopointFieldSchemaDecoder(name).tryDecode(c)
        case Right("date")                => dateFieldSchemaDecoder(name).tryDecode(c)
        case Right("datetime")            => dateTimeFieldSchemaDecoder(name).tryDecode(c)
        case Right(other) =>
          Left(DecodingFailure(s"Field type '$other' for field $name is not supported. Maybe try 'text'?", c.history))
      }
    )

  }

  object json {
    import SearchType.json.given
    import SuggestSchema.json.given

    given textFieldSchemaEncoder: Encoder[TextFieldSchema] = deriveEncoder
    given textFieldSchemaDecoder: Decoder[TextFieldSchema] = deriveDecoder

    given textListFieldSchemaEncoder: Encoder[TextListFieldSchema] = deriveEncoder
    given textListFieldSchemaDecoder: Decoder[TextListFieldSchema] = deriveDecoder

    given intFieldSchemaDecoder: Decoder[IntFieldSchema] = deriveDecoder
    given intFieldSchemaEncoder: Encoder[IntFieldSchema] = deriveEncoder

    given longFieldSchemaDecoder: Decoder[LongFieldSchema] = deriveDecoder
    given longFieldSchemaEncoder: Encoder[LongFieldSchema] = deriveEncoder

    given floatFieldSchemaDecoder: Decoder[FloatFieldSchema] = deriveDecoder
    given floatFieldSchemaEncoder: Encoder[FloatFieldSchema] = deriveEncoder

    given doubleFieldSchemaDecoder: Decoder[DoubleFieldSchema] = deriveDecoder
    given doubleFieldSchemaEncoder: Encoder[DoubleFieldSchema] = deriveEncoder

    given boolFieldSchemaDecoder: Decoder[BooleanFieldSchema] = deriveDecoder
    given boolFieldSchemaEncoder: Encoder[BooleanFieldSchema] = deriveEncoder

    given geopointFieldSchemaDecoder: Decoder[GeopointFieldSchema] = deriveDecoder
    given geopointFieldSchemaEncoder: Encoder[GeopointFieldSchema] = deriveEncoder

    given dateFieldSchemaDecoder: Decoder[DateFieldSchema] = deriveDecoder
    given dateFieldSchemaEncoder: Encoder[DateFieldSchema] = deriveEncoder

    given dateTimeFieldSchemaDecoder: Decoder[DateTimeFieldSchema] = deriveDecoder
    given dateTimeFieldSchemaEncoder: Encoder[DateTimeFieldSchema] = deriveEncoder

    given fieldSchemaEncoder: Encoder[FieldSchema[? <: Field]] = Encoder.instance {
      case f: IntFieldSchema      => intFieldSchemaEncoder.apply(f).deepMerge(withType("int"))
      case f: LongFieldSchema     => longFieldSchemaEncoder.apply(f).deepMerge(withType("long"))
      case f: FloatFieldSchema    => floatFieldSchemaEncoder.apply(f).deepMerge(withType("float"))
      case f: DoubleFieldSchema   => doubleFieldSchemaEncoder.apply(f).deepMerge(withType("double"))
      case f: TextFieldSchema     => textFieldSchemaEncoder.apply(f).deepMerge(withType("text"))
      case f: TextListFieldSchema => textListFieldSchemaEncoder.apply(f).deepMerge(withType("text[]"))
      case f: BooleanFieldSchema  => boolFieldSchemaEncoder.apply(f).deepMerge(withType("bool"))
      case f: GeopointFieldSchema => geopointFieldSchemaEncoder.apply(f).deepMerge(withType("geopoint"))
      case f: DateFieldSchema     => dateFieldSchemaEncoder.apply(f).deepMerge(withType("date"))
      case f: DateTimeFieldSchema => dateTimeFieldSchemaEncoder.apply(f).deepMerge(withType("datetime"))
    }

    given fieldSchemaDecoder: Decoder[FieldSchema[? <: Field]] = Decoder.instance(c =>
      c.downField("type").as[String] match {
        case Right("int")      => intFieldSchemaDecoder.tryDecode(c)
        case Right("long")     => longFieldSchemaDecoder.tryDecode(c)
        case Right("float")    => floatFieldSchemaDecoder.tryDecode(c)
        case Right("double")   => doubleFieldSchemaDecoder.tryDecode(c)
        case Right("bool")     => boolFieldSchemaDecoder.tryDecode(c)
        case Right("text")     => textFieldSchemaDecoder.tryDecode(c)
        case Right("text[]")   => textListFieldSchemaDecoder.tryDecode(c)
        case Right("geopoint") => geopointFieldSchemaDecoder.tryDecode(c)
        case Right("date")     => dateFieldSchemaDecoder.tryDecode(c)
        case Right("datetime") => dateTimeFieldSchemaDecoder.tryDecode(c)
        case Right(other)      => Left(DecodingFailure(s"field type '$other' is not supported", c.history))
        case Left(err)         => Left(err)
      }
    )

    def withType(tpe: String): Json = Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString(tpe))))
  }

}

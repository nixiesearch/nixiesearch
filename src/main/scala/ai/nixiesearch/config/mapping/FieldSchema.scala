package ai.nixiesearch.config

import ai.nixiesearch.config.mapping.SearchParams.QuantStore
import ai.nixiesearch.config.mapping.{FieldName, Language, SearchParams, SuggestSchema}
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Field
import ai.nixiesearch.core.Field.TextLikeField
import ai.nixiesearch.core.codec.FieldCodec
import ai.nixiesearch.core.field.*
import ai.nixiesearch.core.nn.ModelRef
import io.circe.{Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.*
import io.circe.Json
import io.circe.JsonObject

import language.experimental.namedTuples
import scala.NamedTuple.NamedTuple
import scala.util.{Failure, Success}

sealed trait FieldSchema[T <: Field] {
  def name: FieldName
  def store: Boolean
  def sort: Boolean
  def facet: Boolean
  def filter: Boolean

}

object FieldSchema {
  sealed trait TextLikeFieldSchema[T <: TextLikeField] extends FieldSchema[T] {
    def search: SearchParams
    def suggest: Option[SuggestSchema]
  }

  object TextLikeFieldSchema {
    def unapply(
        f: TextLikeFieldSchema[? <: Field]
    ): Option[
      NamedTuple[("name", "search", "suggest"), (FieldName, SearchParams, Option[SuggestSchema])]
    ] = {
      Some(
        NamedTuple[("name", "search", "suggest"), (FieldName, SearchParams, Option[SuggestSchema])](
          (f.name, f.search, f.suggest)
        )
      )
    }

  }

  case class TextFieldSchema(
                              name: FieldName,
                              search: SearchParams = SearchParams(),
                              store: Boolean = true,
                              sort: Boolean = false,
                              facet: Boolean = false,
                              filter: Boolean = false,
                              suggest: Option[SuggestSchema] = None
  ) extends TextLikeFieldSchema[TextField]
      with FieldSchema[TextField]

  case class TextListFieldSchema(
                                  name: FieldName,
                                  search: SearchParams = SearchParams(),
                                  store: Boolean = true,
                                  sort: Boolean = false,
                                  facet: Boolean = false,
                                  filter: Boolean = false,
                                  suggest: Option[SuggestSchema] = None
  ) extends TextLikeFieldSchema[TextListField]
      with FieldSchema[TextListField]

  case class IntFieldSchema(
      name: FieldName,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[IntField]

  case class LongFieldSchema(
      name: FieldName,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[LongField]

  case class FloatFieldSchema(
      name: FieldName,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[FloatField]

  case class DoubleFieldSchema(
      name: FieldName,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[DoubleField]

  case class BooleanFieldSchema(
      name: FieldName,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[BooleanField]

  case class GeopointFieldSchema(
      name: FieldName,
      store: Boolean = true,
      sort: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[GeopointField] {
    def facet = false
  }

  case class DateFieldSchema(
      name: FieldName,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[DateField] {
    def asInt = IntFieldSchema(name, store, sort, facet, filter)
  }

  case class DateTimeFieldSchema(
      name: FieldName,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false
  ) extends FieldSchema[DateTimeField] {
    def asLong = LongFieldSchema(name, store, sort, facet, filter)
  }

  object yaml {
    import SearchParams.given
    import SuggestSchema.yaml.given

    def textFieldSchemaDecoder(name: FieldName): Decoder[TextFieldSchema] = Decoder.instance(c =>
      for {
        search <- c.downField("search").as[Option[SearchParams]]
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
        suggest <- c
          .downField("suggest")
          .as[Option[SuggestSchema]]
          .orElse(c.downField("suggest").as[Option[Boolean]].flatMap {
            case Some(true)  => Right(Some(SuggestSchema()))
            case Some(false) => Right(None)
            case None        => Right(None)
          })
      } yield {
        TextFieldSchema(
          name = name,
          search = search.getOrElse(SearchParams()),
          store = store,
          sort = sort,
          facet = facet,
          filter = filter,
          suggest = suggest
        )
      }
    )
    def textListFieldSchemaDecoder(name: FieldName): Decoder[TextListFieldSchema] = Decoder.instance(c =>
      for {
        search <- c.downField("search").as[Option[SearchParams]]
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))

        suggest <- c
          .downField("suggest")
          .as[Option[SuggestSchema]]
          .orElse(c.downField("suggest").as[Option[Boolean]].flatMap {
            case Some(true)  => Right(Some(SuggestSchema()))
            case Some(false) => Right(None)
            case None        => Right(None)
          })
      } yield {
        TextListFieldSchema(
          name = name,
          search = search.getOrElse(SearchParams()),
          store = store,
          sort = sort,
          facet = facet,
          filter = filter,
          suggest = suggest
        )
      }
    )
    def intFieldSchemaDecoder(name: FieldName): Decoder[IntFieldSchema] = Decoder.instance(c =>
      for {
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        IntFieldSchema(name, store, sort, facet, filter)
      }
    )

    def longFieldSchemaDecoder(name: FieldName): Decoder[LongFieldSchema] = Decoder.instance(c =>
      for {
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        LongFieldSchema(name, store, sort, facet, filter)
      }
    )

    def floatFieldSchemaDecoder(name: FieldName): Decoder[FloatFieldSchema] = Decoder.instance(c =>
      for {
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        FloatFieldSchema(name, store, sort, facet, filter)
      }
    )

    def doubleFieldSchemaDecoder(name: FieldName): Decoder[DoubleFieldSchema] = Decoder.instance(c =>
      for {
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        DoubleFieldSchema(name, store, sort, facet, filter)
      }
    )

    def booleanFieldSchemaDecoder(name: FieldName): Decoder[BooleanFieldSchema] = Decoder.instance(c =>
      for {
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        BooleanFieldSchema(name, store, sort, facet, filter)
      }
    )
    def dateFieldSchemaDecoder(name: FieldName): Decoder[DateFieldSchema] = Decoder.instance(c =>
      for {
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        DateFieldSchema(name, store, sort, facet, filter)
      }
    )
    def dateTimeFieldSchemaDecoder(name: FieldName): Decoder[DateTimeFieldSchema] = Decoder.instance(c =>
      for {
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet  <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        DateTimeFieldSchema(name, store, sort, facet, filter)
      }
    )

    def geopointFieldSchemaDecoder(name: FieldName): Decoder[GeopointFieldSchema] = Decoder.instance(c =>
      for {
        store  <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        filter <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        GeopointFieldSchema(name, store, sort, filter)
      }
    )

    def fieldSchemaDecoder(name: FieldName): Decoder[FieldSchema[? <: Field]] = Decoder.instance(c =>
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
    import SearchParams.given
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

    given geopointFieldSchemaDecoder: Decoder[GeopointFieldSchema] = Decoder.instance(c =>
      for {
        name   <- c.downField("name").as[FieldName]
        store  <- c.downField("store").as[Boolean]
        sort   <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false)) // compat with 0.4
        filter <- c.downField("filter").as[Boolean]
      } yield {
        GeopointFieldSchema(name, store, sort, filter)
      }
    )
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

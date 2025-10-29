package ai.nixiesearch.config

import ai.nixiesearch.config.mapping.FieldName.StringName
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
import io.circe.derivation.{ConfiguredDecoder, ConfiguredEncoder}

import scala.NamedTuple.NamedTuple
import scala.util.{Failure, Success}

sealed trait FieldSchema[T <: Field] {
  type Self <: FieldSchema[T]
  def name: FieldName
  def store: Boolean
  def sort: Boolean
  def facet: Boolean
  def filter: Boolean
  def required: Boolean

  def codec: FieldCodec[T, Self, ?]
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

  case class IdFieldSchema(name: FieldName = StringName("_id"), sort: Boolean = false, filter: Boolean = true)
      extends FieldSchema[IdField] {

    def store: Boolean                 = true
    def facet: Boolean                 = false
    def suggest: Option[SuggestSchema] = None
    def required: Boolean              = false

    type Self = IdFieldSchema
    val codec: FieldCodec[IdField, IdFieldSchema, ?] = IdField
  }

  case class TextFieldSchema(
      name: FieldName,
      search: SearchParams = SearchParams(),
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false,
      suggest: Option[SuggestSchema] = None,
      required: Boolean = false
  ) extends TextLikeFieldSchema[TextField]
      with FieldSchema[TextField] {
    type Self = TextFieldSchema
    val codec: FieldCodec[TextField, TextFieldSchema, ?] = TextField
  }

  case class TextListFieldSchema(
      name: FieldName,
      search: SearchParams = SearchParams(),
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false,
      suggest: Option[SuggestSchema] = None,
      required: Boolean = false
  ) extends TextLikeFieldSchema[TextListField]
      with FieldSchema[TextListField] {
    type Self = TextListFieldSchema
    val codec: FieldCodec[TextListField, TextListFieldSchema, ?] = TextListField

  }

  case class IntFieldSchema(
      name: FieldName,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false,
      required: Boolean = false
  ) extends FieldSchema[IntField] {
    type Self = IntFieldSchema
    val codec: FieldCodec[IntField, IntFieldSchema, ?] = IntField
  }

  case class IntListFieldSchema(
      name: FieldName,
      store: Boolean = true,
      facet: Boolean = false,
      filter: Boolean = false,
      required: Boolean = false
  ) extends FieldSchema[IntListField] {
    def sort = false
    type Self = IntListFieldSchema
    val codec: FieldCodec[IntListField, IntListFieldSchema, ?] = IntListField
  }

  case class LongFieldSchema(
      name: FieldName,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false,
      required: Boolean = false
  ) extends FieldSchema[LongField] {
    type Self = LongFieldSchema
    val codec: FieldCodec[LongField, LongFieldSchema, ?] = LongField
  }

  case class LongListFieldSchema(
      name: FieldName,
      store: Boolean = true,
      facet: Boolean = false,
      filter: Boolean = false,
      required: Boolean = false
  ) extends FieldSchema[LongListField] {
    def sort = false
    type Self = LongListFieldSchema
    val codec: FieldCodec[LongListField, LongListFieldSchema, ?] = LongListField
  }

  case class FloatFieldSchema(
      name: FieldName,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false,
      required: Boolean = false
  ) extends FieldSchema[FloatField] {
    type Self = FloatFieldSchema
    val codec: FieldCodec[FloatField, FloatFieldSchema, ?] = FloatField
  }

  case class FloatListFieldSchema(
      name: FieldName,
      store: Boolean = true,
      facet: Boolean = false,
      filter: Boolean = false,
      required: Boolean = false
  ) extends FieldSchema[FloatListField] {
    def sort = false
    type Self = FloatListFieldSchema
    val codec: FieldCodec[FloatListField, FloatListFieldSchema, ?] = FloatListField
  }

  case class DoubleFieldSchema(
      name: FieldName,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false,
      required: Boolean = false
  ) extends FieldSchema[DoubleField] {
    type Self = DoubleFieldSchema
    val codec: FieldCodec[DoubleField, DoubleFieldSchema, ?] = DoubleField
  }

  case class DoubleListFieldSchema(
      name: FieldName,
      store: Boolean = true,
      facet: Boolean = false,
      filter: Boolean = false,
      required: Boolean = false
  ) extends FieldSchema[DoubleListField] {
    def sort = false
    type Self = DoubleListFieldSchema
    val codec: FieldCodec[DoubleListField, DoubleListFieldSchema, ?] = DoubleListField
  }

  case class BooleanFieldSchema(
      name: FieldName,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false,
      required: Boolean = false
  ) extends FieldSchema[BooleanField] {
    type Self = BooleanFieldSchema
    val codec: FieldCodec[BooleanField, BooleanFieldSchema, ?] = BooleanField

  }

  case class GeopointFieldSchema(
      name: FieldName,
      store: Boolean = true,
      sort: Boolean = false,
      filter: Boolean = false,
      required: Boolean = false
  ) extends FieldSchema[GeopointField] {
    def facet = false
    type Self = GeopointFieldSchema
    val codec: FieldCodec[GeopointField, GeopointFieldSchema, ?] = GeopointField
    
  }

  case class DateFieldSchema(
      name: FieldName,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false,
      required: Boolean = false
  ) extends FieldSchema[DateField] {
    def asInt = IntFieldSchema(name, store, sort, facet, filter)
    type Self = DateFieldSchema
    val codec: FieldCodec[DateField, DateFieldSchema, ?] = DateField
  }

  case class DateTimeFieldSchema(
      name: FieldName,
      store: Boolean = true,
      sort: Boolean = false,
      facet: Boolean = false,
      filter: Boolean = false,
      required: Boolean = false
  ) extends FieldSchema[DateTimeField] {
    def asLong = LongFieldSchema(name, store, sort, facet, filter)
    type Self = DateTimeFieldSchema
    val codec: FieldCodec[DateTimeField, DateTimeFieldSchema, ?] = DateTimeField
  }

  object yaml {
    import SearchParams.given
    import SuggestSchema.yaml.given

    def idFieldSchemaDecoder(name: FieldName): Decoder[IdFieldSchema] = Decoder.instance(c =>
      for {
        sort   <- c.downField("sort").as[Option[Boolean]]
        filter <- c.downField("filter").as[Option[Boolean]]
      } yield {
        val default = IdFieldSchema()
        IdFieldSchema(name = name, sort = sort.getOrElse(default.sort), filter = filter.getOrElse(default.filter))
      }
    )

    def textFieldSchemaDecoder(name: FieldName): Decoder[TextFieldSchema] = Decoder.instance(c =>
      for {
        search  <- c.downField("search").as[Option[SearchParams]]
        store   <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort    <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet   <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter  <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
        suggest <- c
          .downField("suggest")
          .as[Option[SuggestSchema]]
          .orElse(c.downField("suggest").as[Option[Boolean]].flatMap {
            case Some(true)  => Right(Some(SuggestSchema()))
            case Some(false) => Right(None)
            case None        => Right(None)
          })
        required <- c.downField("required").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        TextFieldSchema(
          name = name,
          search = search.getOrElse(SearchParams()),
          store = store,
          sort = sort,
          facet = facet,
          filter = filter,
          suggest = suggest,
          required = required
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
        required <- c.downField("required").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        TextListFieldSchema(
          name = name,
          search = search.getOrElse(SearchParams()),
          store = store,
          sort = sort,
          facet = facet,
          filter = filter,
          suggest = suggest,
          required = required
        )
      }
    )
    def intFieldSchemaDecoder(name: FieldName): Decoder[IntFieldSchema] = Decoder.instance(c =>
      for {
        store    <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort     <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet    <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter   <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
        required <- c.downField("required").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        IntFieldSchema(name = name, store = store, sort = sort, facet = facet, filter = filter, required = required)
      }
    )

    def intListFieldSchemaDecoder(name: FieldName): Decoder[IntListFieldSchema] = Decoder.instance(c =>
      for {
        store    <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        facet    <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter   <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
        required <- c.downField("required").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        IntListFieldSchema(name = name, store = store, facet = facet, filter = filter, required = required)
      }
    )

    def longFieldSchemaDecoder(name: FieldName): Decoder[LongFieldSchema] = Decoder.instance(c =>
      for {
        store    <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort     <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet    <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter   <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
        required <- c.downField("required").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        LongFieldSchema(name = name, store = store, sort = sort, facet = facet, filter = filter, required = required)
      }
    )

    def longListFieldSchemaDecoder(name: FieldName): Decoder[LongListFieldSchema] = Decoder.instance(c =>
      for {
        store    <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        facet    <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter   <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
        required <- c.downField("required").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        LongListFieldSchema(name = name, store = store, facet = facet, filter = filter, required = required)
      }
    )

    def floatFieldSchemaDecoder(name: FieldName): Decoder[FloatFieldSchema] = Decoder.instance(c =>
      for {
        store    <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort     <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet    <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter   <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
        required <- c.downField("required").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        FloatFieldSchema(name = name, store = store, sort = sort, facet = facet, filter = filter, required = required)
      }
    )
    def floatListFieldSchemaDecoder(name: FieldName): Decoder[FloatListFieldSchema] = Decoder.instance(c =>
      for {
        store    <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        facet    <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter   <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
        required <- c.downField("required").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        FloatListFieldSchema(name = name, store = store, facet = facet, filter = filter, required = required)
      }
    )

    def doubleFieldSchemaDecoder(name: FieldName): Decoder[DoubleFieldSchema] = Decoder.instance(c =>
      for {
        store    <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort     <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet    <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter   <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
        required <- c.downField("required").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        DoubleFieldSchema(name = name, store = store, sort = sort, facet = facet, filter = filter, required = required)
      }
    )

    def doubleListFieldSchemaDecoder(name: FieldName): Decoder[DoubleListFieldSchema] = Decoder.instance(c =>
      for {
        store    <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        facet    <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter   <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
        required <- c.downField("required").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        DoubleListFieldSchema(name = name, store = store, facet = facet, filter = filter, required = required)
      }
    )

    def booleanFieldSchemaDecoder(name: FieldName): Decoder[BooleanFieldSchema] = Decoder.instance(c =>
      for {
        store    <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort     <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet    <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter   <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
        required <- c.downField("required").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        BooleanFieldSchema(name = name, store = store, sort = sort, facet = facet, filter = filter, required = required)
      }
    )
    def dateFieldSchemaDecoder(name: FieldName): Decoder[DateFieldSchema] = Decoder.instance(c =>
      for {
        store    <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort     <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet    <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter   <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
        required <- c.downField("required").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        DateFieldSchema(name = name, store = store, sort = sort, facet = facet, filter = filter, required = required)
      }
    )
    def dateTimeFieldSchemaDecoder(name: FieldName): Decoder[DateTimeFieldSchema] = Decoder.instance(c =>
      for {
        store    <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort     <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        facet    <- c.downField("facet").as[Option[Boolean]].map(_.getOrElse(false))
        filter   <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
        required <- c.downField("required").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        DateTimeFieldSchema(
          name = name,
          store = store,
          sort = sort,
          facet = facet,
          filter = filter,
          required = required
        )
      }
    )

    def geopointFieldSchemaDecoder(name: FieldName): Decoder[GeopointFieldSchema] = Decoder.instance(c =>
      for {
        store    <- c.downField("store").as[Option[Boolean]].map(_.getOrElse(true))
        sort     <- c.downField("sort").as[Option[Boolean]].map(_.getOrElse(false))
        filter   <- c.downField("filter").as[Option[Boolean]].map(_.getOrElse(false))
        required <- c.downField("required").as[Option[Boolean]].map(_.getOrElse(false))
      } yield {
        GeopointFieldSchema(name, store, sort, filter, required)
      }
    )

    def fieldSchemaDecoder(name: FieldName): Decoder[FieldSchema[? <: Field]] = Decoder.instance(c =>
      c.downField("type").as[String] match {
        case Left(value) => Left(DecodingFailure(s"Cannot decode field '$name': $value", c.history))
        case Right("id") => idFieldSchemaDecoder(name).tryDecode(c)
        case Right("text" | "string") if name.name == "_id" => idFieldSchemaDecoder(name).tryDecode(c)
        case Right("text" | "string")                       => textFieldSchemaDecoder(name).tryDecode(c)
        case Right("text[]" | "string[]")                   => textListFieldSchemaDecoder(name).tryDecode(c)
        case Right("int")                                   => intFieldSchemaDecoder(name).tryDecode(c)
        case Right("int[]")                                 => intListFieldSchemaDecoder(name).tryDecode(c)
        case Right("long")                                  => longFieldSchemaDecoder(name).tryDecode(c)
        case Right("long[]")                                => longListFieldSchemaDecoder(name).tryDecode(c)
        case Right("float")                                 => floatFieldSchemaDecoder(name).tryDecode(c)
        case Right("float[]")                               => floatListFieldSchemaDecoder(name).tryDecode(c)
        case Right("double")                                => doubleFieldSchemaDecoder(name).tryDecode(c)
        case Right("double[]")                              => doubleListFieldSchemaDecoder(name).tryDecode(c)
        case Right("bool")                                  => booleanFieldSchemaDecoder(name).tryDecode(c)
        case Right("geopoint")                              => geopointFieldSchemaDecoder(name).tryDecode(c)
        case Right("date")                                  => dateFieldSchemaDecoder(name).tryDecode(c)
        case Right("datetime")                              => dateTimeFieldSchemaDecoder(name).tryDecode(c)
        case Right(other)                                   =>
          Left(DecodingFailure(s"Field type '$other' for field $name is not supported. Maybe try 'text'?", c.history))
      }
    )

  }

  object json {
    import SearchParams.given
    import SuggestSchema.json.given
    import io.circe.derivation.Configuration

    given config: Configuration = Configuration.default.withDefaults

    given idFieldSchemaEncoder: Encoder[IdFieldSchema] = deriveEncoder
    given idfieldSchemaDecoder: Decoder[IdFieldSchema] = ConfiguredDecoder.derived[IdFieldSchema](using config)

    given textFieldSchemaEncoder: Encoder[TextFieldSchema] = deriveEncoder
    given textFieldSchemaDecoder: Decoder[TextFieldSchema] = ConfiguredDecoder.derived[TextFieldSchema](using config)

    given textListFieldSchemaEncoder: Encoder[TextListFieldSchema] = deriveEncoder
    given textListFieldSchemaDecoder: Decoder[TextListFieldSchema] =
      ConfiguredDecoder.derived[TextListFieldSchema](using config)

    given intFieldSchemaDecoder: Decoder[IntFieldSchema] = ConfiguredDecoder.derived[IntFieldSchema](using config)
    given intFieldSchemaEncoder: Encoder[IntFieldSchema] = deriveEncoder

    given intListFieldSchemaDecoder: Decoder[IntListFieldSchema] =
      ConfiguredDecoder.derived[IntListFieldSchema](using config)
    given intListFieldSchemaEncoder: Encoder[IntListFieldSchema] = deriveEncoder

    given longFieldSchemaDecoder: Decoder[LongFieldSchema] = ConfiguredDecoder.derived[LongFieldSchema](using config)
    given longFieldSchemaEncoder: Encoder[LongFieldSchema] = deriveEncoder

    given longListFieldSchemaDecoder: Decoder[LongListFieldSchema] =
      ConfiguredDecoder.derived[LongListFieldSchema](using config)
    given longListFieldSchemaEncoder: Encoder[LongListFieldSchema] = deriveEncoder

    given floatFieldSchemaDecoder: Decoder[FloatFieldSchema] = ConfiguredDecoder.derived[FloatFieldSchema](using config)
    given floatFieldSchemaEncoder: Encoder[FloatFieldSchema] = deriveEncoder

    given floatListFieldSchemaDecoder: Decoder[FloatListFieldSchema] =
      ConfiguredDecoder.derived[FloatListFieldSchema](using config)
    given floatListFieldSchemaEncoder: Encoder[FloatListFieldSchema] = deriveEncoder

    given doubleFieldSchemaDecoder: Decoder[DoubleFieldSchema] =
      ConfiguredDecoder.derived[DoubleFieldSchema](using config)
    given doubleFieldSchemaEncoder: Encoder[DoubleFieldSchema] = deriveEncoder

    given doubleListFieldSchemaDecoder: Decoder[DoubleListFieldSchema] =
      ConfiguredDecoder.derived[DoubleListFieldSchema](using config)
    given doubleListFieldSchemaEncoder: Encoder[DoubleListFieldSchema] = deriveEncoder

    given boolFieldSchemaDecoder: Decoder[BooleanFieldSchema] =
      ConfiguredDecoder.derived[BooleanFieldSchema](using config)
    given boolFieldSchemaEncoder: Encoder[BooleanFieldSchema] = deriveEncoder

    given geopointFieldSchemaDecoder: Decoder[GeopointFieldSchema] =
      ConfiguredDecoder.derived[GeopointFieldSchema](using config)
    given geopointFieldSchemaEncoder: Encoder[GeopointFieldSchema] = deriveEncoder

    given dateFieldSchemaDecoder: Decoder[DateFieldSchema] = ConfiguredDecoder.derived[DateFieldSchema](using config)
    given dateFieldSchemaEncoder: Encoder[DateFieldSchema] = deriveEncoder

    given dateTimeFieldSchemaDecoder: Decoder[DateTimeFieldSchema] =
      ConfiguredDecoder.derived[DateTimeFieldSchema](using config)
    given dateTimeFieldSchemaEncoder: Encoder[DateTimeFieldSchema] = deriveEncoder

    given fieldSchemaEncoder: Encoder[FieldSchema[? <: Field]] = Encoder.instance {
      case f: IdFieldSchema         => idFieldSchemaEncoder.apply(f).deepMerge(withType("id"))
      case f: IntFieldSchema        => intFieldSchemaEncoder.apply(f).deepMerge(withType("int"))
      case f: IntListFieldSchema    => intListFieldSchemaEncoder.apply(f).deepMerge(withType("int[]"))
      case f: LongFieldSchema       => longFieldSchemaEncoder.apply(f).deepMerge(withType("long"))
      case f: LongListFieldSchema   => longListFieldSchemaEncoder.apply(f).deepMerge(withType("long[]"))
      case f: FloatFieldSchema      => floatFieldSchemaEncoder.apply(f).deepMerge(withType("float"))
      case f: FloatListFieldSchema  => floatListFieldSchemaEncoder.apply(f).deepMerge(withType("float[]"))
      case f: DoubleFieldSchema     => doubleFieldSchemaEncoder.apply(f).deepMerge(withType("double"))
      case f: DoubleListFieldSchema => doubleListFieldSchemaEncoder.apply(f).deepMerge(withType("double[]"))
      case f: TextFieldSchema       => textFieldSchemaEncoder.apply(f).deepMerge(withType("text"))
      case f: TextListFieldSchema   => textListFieldSchemaEncoder.apply(f).deepMerge(withType("text[]"))
      case f: BooleanFieldSchema    => boolFieldSchemaEncoder.apply(f).deepMerge(withType("bool"))
      case f: GeopointFieldSchema   => geopointFieldSchemaEncoder.apply(f).deepMerge(withType("geopoint"))
      case f: DateFieldSchema       => dateFieldSchemaEncoder.apply(f).deepMerge(withType("date"))
      case f: DateTimeFieldSchema   => dateTimeFieldSchemaEncoder.apply(f).deepMerge(withType("datetime"))
    }

    given fieldSchemaDecoder: Decoder[FieldSchema[? <: Field]] = Decoder.instance(c =>
      c.downField("type").as[String] match {
        case Right("int")      => intFieldSchemaDecoder.tryDecode(c)
        case Right("int[]")    => intListFieldSchemaDecoder.tryDecode(c)
        case Right("long")     => longFieldSchemaDecoder.tryDecode(c)
        case Right("long[]")   => longListFieldSchemaDecoder.tryDecode(c)
        case Right("float")    => floatFieldSchemaDecoder.tryDecode(c)
        case Right("float[]")  => floatListFieldSchemaDecoder.tryDecode(c)
        case Right("double")   => doubleFieldSchemaDecoder.tryDecode(c)
        case Right("double[]") => doubleListFieldSchemaDecoder.tryDecode(c)
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

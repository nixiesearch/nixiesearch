package ai.nixiesearch.api.filter

import ai.nixiesearch.api.filter.Predicate.BoolPredicate.{AndPredicate, NotPredicate, OrPredicate}
import ai.nixiesearch.api.filter.Predicate.FilterTerm.{BooleanTerm, NumTerm, StringTerm}
import ai.nixiesearch.api.filter.Predicate.GeoBoundingBoxPredicate.{geoBoxDecoder, geoBoxEncoder}
import ai.nixiesearch.api.filter.Predicate.GeoDistancePredicate.{geoDistanceDecoder, geoDistanceEncoder}
import ai.nixiesearch.config.FieldSchema.{
  BooleanFieldSchema,
  DoubleFieldSchema,
  FloatFieldSchema,
  IntFieldSchema,
  LongFieldSchema,
  TextLikeFieldSchema
}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.FiniteRange.{Higher, Lower}
import ai.nixiesearch.core.{FiniteRange, Logging}
import ai.nixiesearch.core.codec.TextFieldCodec
import ai.nixiesearch.util.Distance
import cats.effect.IO
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.{BooleanClause, BooleanQuery, ConstantScoreQuery, TermQuery, Query as LuceneQuery}
import cats.implicits.*
import io.circe.generic.semiauto.{deriveCodec, deriveEncoder}
import org.apache.lucene.document.{IntField, LatLonDocValuesField, LatLonPoint, LatLonPointDistanceQuery, LongField}
import io.circe.syntax.*
import scala.util.{Failure, Success}
import org.apache.lucene.document.{IntField, LongField}
import language.experimental.namedTuples

sealed trait Predicate {
  def compile(mapping: IndexMapping): IO[LuceneQuery]
}

object Predicate {
  sealed trait BoolPredicate extends Predicate {
    def predicates: List[Predicate]
  }
  object BoolPredicate {
    case class AndPredicate(predicates: List[Predicate]) extends BoolPredicate {
      override def compile(mapping: IndexMapping): IO[LuceneQuery] =
        combine(predicates, mapping, Occur.FILTER).map(_.build())
    }
    case class OrPredicate(predicates: List[Predicate]) extends BoolPredicate {
      override def compile(mapping: IndexMapping): IO[LuceneQuery] =
        combine(predicates, mapping, Occur.SHOULD).map(b => {
          b.setMinimumNumberShouldMatch(1)
          new ConstantScoreQuery(b.build())
        })
    }
    case class NotPredicate(predicates: List[Predicate]) extends BoolPredicate {
      override def compile(mapping: IndexMapping): IO[LuceneQuery] =
        combine(predicates, mapping, Occur.MUST_NOT).map(_.build())
    }

    def combine(predicates: List[Predicate], mapping: IndexMapping, occur: Occur): IO[BooleanQuery.Builder] = for {
      builder <- IO.pure(new BooleanQuery.Builder())
      _       <- predicates.traverse(p => p.compile(mapping).map(q => builder.add(new BooleanClause(q, occur)))).void
    } yield {
      builder
    }

    given boolPredicateEncoder: Encoder[BoolPredicate] = Encoder.instance { bool =>
      Json.fromValues(bool.predicates.map(p => predicateEncoder(p)))
    }

  }

  enum FilterTerm {
    case StringTerm(value: String)   extends FilterTerm
    case BooleanTerm(value: Boolean) extends FilterTerm
    case NumTerm(value: Long)        extends FilterTerm
  }

  object FilterTerm {
    given filterTermEncoder: Encoder[FilterTerm] = Encoder.instance {
      case StringTerm(value)  => Json.fromString(value)
      case BooleanTerm(value) => Json.fromBoolean(value)
      case NumTerm(value)     => Json.fromLong(value)
    }
    given filterTermDecoder: Decoder[FilterTerm] = Decoder.instance(c =>
      c.focus match {
        case None => Left(DecodingFailure("got empty json for a term", c.history))
        case Some(json) =>
          json.fold(
            jsonNull = Left(DecodingFailure("got null instead of term", c.history)),
            jsonBoolean = b => Right(BooleanTerm(b)),
            jsonNumber = num =>
              num.toLong match {
                case Some(long) => Right(NumTerm(long))
                case None       => Left(DecodingFailure(s"cannot convert $num to long to use as a term", c.history))
              },
            jsonString = s => Right(StringTerm(s)),
            jsonArray = s => Left(DecodingFailure("arrays are not supported as filter terms", c.history)),
            jsonObject = o => Left(DecodingFailure("objects are not supported as filter terms", c.history))
          )
      }
    )
  }

  case class TermPredicate(field: String, value: FilterTerm) extends Predicate with Logging {
    def compile(mapping: IndexMapping): IO[LuceneQuery] = {
      IO((mapping.fields.get(field), value)).flatMap {
        case (None, _) =>
          IO.raiseError(UserError(s"field $field is not defined in index mapping"))
        case (Some(schema), _) if !schema.filter =>
          IO.raiseError(UserError(s"Cannot filter over a non-filterable field '$field'"))
        case (Some(schema: TextLikeFieldSchema[?]), FilterTerm.StringTerm(value)) if schema.filter =>
          IO(new TermQuery(new Term(field + TextFieldCodec.RAW_SUFFIX, value)))
        case (Some(schema: TextLikeFieldSchema[?]), other) =>
          IO.raiseError(UserError(s"field $field expects string filter term, but got $other"))
        case (Some(schema: IntFieldSchema), FilterTerm.NumTerm(value)) if schema.filter =>
          if ((value <= Int.MaxValue) && (value >= Int.MinValue))
            IO(IntField.newExactQuery(field, value.toInt))
          else
            IO.raiseError(UserError(s"field $field is int field, but term $value cannot be cast to int safely"))
        case (Some(schema: LongFieldSchema), FilterTerm.NumTerm(value)) if schema.filter =>
          IO(LongField.newExactQuery(field, value))
        case (Some(schema: BooleanFieldSchema), FilterTerm.BooleanTerm(value)) if schema.filter =>
          IO(IntField.newExactQuery(field, if (value) 1 else 0))
        case (Some(other), _) =>
          IO.raiseError(
            UserError(s"Term filtering only works with text/bool/int/long fields, and field '$field' is $other")
          )
      }
    }
  }

  object TermPredicate {
    def apply(field: String, value: String)  = new TermPredicate(field, StringTerm(value))
    def apply(field: String, value: Int)     = new TermPredicate(field, NumTerm(value))
    def apply(field: String, value: Long)    = new TermPredicate(field, NumTerm(value))
    def apply(field: String, value: Boolean) = new TermPredicate(field, BooleanTerm(value))
    given termPredicateEncoder: Encoder[TermPredicate] =
      Encoder.instance(t => Json.fromJsonObject(JsonObject.singleton(t.field, FilterTerm.filterTermEncoder(t.value))))

    given termPredicateDecoder: Decoder[TermPredicate] = Decoder.instance(c =>
      c.value.asObject match {
        case Some(obj) =>
          obj.toList match {
            case ((field, json) :: Nil) =>
              FilterTerm.filterTermDecoder.decodeJson(json) match {
                case Left(value)  => Left(value)
                case Right(value) => Right(TermPredicate(field, value))
              }
            case Nil   => Left(DecodingFailure(s"term predicate should be a non-empty json object: $obj", c.history))
            case other => Left(DecodingFailure(s"term predicate can contain only a single field: $other", c.history))
          }
        case None => Left(DecodingFailure(s"term predicate should be a json object: ${c.value}", c.history))
      }
    )
  }

  sealed trait RangePredicate extends Predicate {
    def field: String
  }
  object RangePredicate {
    case class RangeGt(field: String, greaterThan: FiniteRange.Lower) extends RangePredicate {
      override def compile(mapping: IndexMapping): IO[LuceneQuery] =
        build(field, mapping, greaterThan, Higher.POSITIVE_INF)
    }
    case class RangeLt(field: String, lessThan: FiniteRange.Higher) extends RangePredicate {
      override def compile(mapping: IndexMapping): IO[LuceneQuery] =
        build(field, mapping, Lower.NEGATIVE_INF, lessThan)
    }
    case class RangeGtLt(field: String, greaterThan: FiniteRange.Lower, lessThan: FiniteRange.Higher)
        extends RangePredicate {
      override def compile(mapping: IndexMapping): IO[LuceneQuery] =
        build(field, mapping, greaterThan, lessThan)
    }

    def build(
        field: String,
        mapping: IndexMapping,
        greaterThan: FiniteRange.Lower,
        smallerThan: FiniteRange.Higher
    ): IO[LuceneQuery] = {
      mapping.fields.get(field) match {
        case Some(IntFieldSchema(filter=true)) =>
          IO {
            val lower = greaterThan match {
              case FiniteRange.Lower.Gt(value)  => math.round(value).toInt + 1
              case FiniteRange.Lower.Gte(value) => math.round(value).toInt
            }
            val higher = smallerThan match {
              case FiniteRange.Higher.Lt(value)  => math.round(value).toInt - 1
              case FiniteRange.Higher.Lte(value) => math.round(value).toInt
            }
            org.apache.lucene.document.IntField.newRangeQuery(field, lower, higher)
          }
        case Some(LongFieldSchema(filter=true)) =>
          IO {
            val lower = greaterThan match {
              case FiniteRange.Lower.Gt(value)  => math.round(value) + 1
              case FiniteRange.Lower.Gte(value) => math.round(value)
            }
            val higher = smallerThan match {
              case FiniteRange.Higher.Lt(value)  => math.round(value) - 1
              case FiniteRange.Higher.Lte(value) => math.round(value)
            }
            org.apache.lucene.document.LongField.newRangeQuery(field, lower, higher)
          }

        case Some(LongFieldSchema(filter=false)) =>
          IO.raiseError(new Exception(s"range query for field '$field' only works with filter=true fields"))
        case Some(IntFieldSchema(filter=false)) =>
          IO.raiseError(new Exception(s"range query for field '$field' only works with filter=true fields"))
        case Some(FloatFieldSchema(filter=true)) =>
          IO {
            val lower = greaterThan match {
              case FiniteRange.Lower.Gt(value)  => Math.nextUp(value.toFloat)
              case FiniteRange.Lower.Gte(value) => value.toFloat
            }

            val higher = smallerThan match {
              case FiniteRange.Higher.Lt(value)  => Math.nextDown(value.toFloat)
              case FiniteRange.Higher.Lte(value) => value.toFloat
            }
            org.apache.lucene.document.FloatField.newRangeQuery(field, lower, higher)
          }
        case Some(DoubleFieldSchema(filter=true)) =>
          IO {
            val lower = greaterThan match {
              case FiniteRange.Lower.Gt(value)  => Math.nextUp(value)
              case FiniteRange.Lower.Gte(value) => value
            }

            val higher = smallerThan match {
              case FiniteRange.Higher.Lt(value)  => Math.nextDown(value)
              case FiniteRange.Higher.Lte(value) => value
            }
            org.apache.lucene.document.DoubleField.newRangeQuery(field, lower, higher)
          }

        case Some(FloatFieldSchema(filter=false)) =>
          IO.raiseError(new Exception(s"range query for field '$field' only works with filter=true fields"))
        case Some(other) => IO.raiseError(new Exception(s"range queries only work with numeric fields: $other"))
        case None        => IO.raiseError(new Exception(s"cannot execute range query over non-existent field $field"))
      }
    }

    def hilow(greaterThan: FiniteRange.Lower, smallerThan: FiniteRange.Higher) = {
      val lower = greaterThan match {
        case FiniteRange.Lower.Gt(value)  => Math.nextUp(value.toFloat)
        case FiniteRange.Lower.Gte(value) => value.toFloat
      }

      val higher = smallerThan match {
        case FiniteRange.Higher.Lt(value)  => Math.nextDown(value.toFloat)
        case FiniteRange.Higher.Lte(value) => value.toFloat
      }
      (lower, higher)
    }

    given rangeDecoder: Decoder[RangePredicate] = Decoder.instance(c => {
      c.keys.map(_.toList) match {
        case None      => Left(DecodingFailure(s"cannot decode range without a field", c.history))
        case Some(Nil) => Left(DecodingFailure(s"cannot decode range without a field", c.history))
        case Some(field :: Nil) =>
          for {
            gt  <- c.downField(field).downField("gt").as[Option[Double]]
            gte <- c.downField(field).downField("gte").as[Option[Double]]
            lower <- (gt, gte) match {
              case (Some(gt), None)  => Right(Some(Lower.Gt(gt)))
              case (None, Some(gte)) => Right(Some(Lower.Gte(gte)))
              case (None, None)      => Right(None)
              case (Some(_), Some(_)) =>
                Left(DecodingFailure(s"got both gt and gte fields for range, expected one", c.history))
            }
            lt  <- c.downField(field).downField("lt").as[Option[Double]]
            lte <- c.downField(field).downField("lte").as[Option[Double]]
            higher <- (lt, lte) match {
              case (Some(lt), None)  => Right(Some(Higher.Lt(lt)))
              case (None, Some(lte)) => Right(Some(Higher.Lte(lte)))
              case (None, None)      => Right(None)
              case (Some(_), Some(_)) =>
                Left(DecodingFailure("got both lt and lte fields for a range, expected one", c.history))
            }
            range <- (lower, higher) match {
              case (Some(low), Some(high)) => Right(RangeGtLt(field, low, high))
              case (Some(low), None)       => Right(RangeGt(field, low))
              case (None, Some(high))      => Right(RangeLt(field, high))
              case (None, None) =>
                Left(DecodingFailure(s"cannot decode range without gt/gte/lt/lte predicates", c.history))
            }
          } yield {
            range
          }
        case Some(head :: tail) =>
          Left(
            DecodingFailure(
              s"range filter can only be defined for a single field, got head=$head tail=$tail",
              c.history
            )
          )
      }
    })

    given rangePredicateEncoder: Encoder[RangePredicate] = Encoder.instance {
      case RangeGt(field, gt) => json(field, JsonObject.singleton(gt.name, Json.fromDoubleOrNull(gt.value)))
      case RangeLt(field, lt) => json(field, JsonObject.singleton(lt.name, Json.fromDoubleOrNull(lt.value)))
      case RangeGtLt(field, gt, lt) =>
        json(
          field,
          JsonObject.fromIterable(
            List(
              gt.name -> Json.fromDoubleOrNull(gt.value),
              lt.name -> Json.fromDoubleOrNull(lt.value)
            )
          )
        )
    }

  }

  case class LatLon(lat: Double, lon: Double)
  object LatLon {
    given latLonCodec: Codec[LatLon] = deriveCodec
  }
  case class GeoDistancePredicate(field: String, point: LatLon, distance: Distance) extends Predicate with Logging {
    override def compile(mapping: IndexMapping): IO[LuceneQuery] = {
      mapping.geopointFields.get(field) match {
        case None => IO.raiseError(UserError("cannot perform geo_distance query over non-geopoint field"))
        case Some(schema) if !schema.filter =>
          IO.raiseError(UserError("cannot perform geo_distance query over non-filterable geopoint field"))
        case Some(schema) =>
          IO.pure(LatLonPoint.newDistanceQuery(field, point.lat, point.lon, distance.meters))
      }
    }
  }

  object GeoDistancePredicate {
    given geoDistanceDecoder: Decoder[GeoDistancePredicate] = Decoder.instance(c =>
      for {
        distance <- c.downField("distance").as[Distance]
        field    <- c.downField("field").as[String]
        lat      <- c.downField("lat").as[Double]
        lon      <- c.downField("lon").as[Double]
      } yield {
        GeoDistancePredicate(field, LatLon(lat, lon), distance)
      }
    )

    given geoDistanceEncoder: Encoder[GeoDistancePredicate] = Encoder.instance(c =>
      Json.obj(
        "distance" -> c.distance.asJson,
        "field"    -> Json.fromString(c.field),
        "lat"      -> Json.fromDoubleOrNull(c.point.lat),
        "lon"      -> Json.fromDoubleOrNull(c.point.lon)
      )
    )
  }

  case class GeoBoundingBoxPredicate(field: String, topLeft: LatLon, bottomRight: LatLon)
      extends Predicate
      with Logging {
    override def compile(mapping: IndexMapping): IO[LuceneQuery] = {
      mapping.geopointFields.get(field) match {
        case None => IO.raiseError(UserError("cannot perform geo_distance query over non-geopoint field"))
        case Some(schema) if !schema.filter =>
          IO.raiseError(UserError("cannot perform geo_distance query over non-filterable geopoint field"))
        case Some(schema) =>
          IO.pure(
            LatLonPoint.newBoxQuery(
              field,
              math.min(topLeft.lat, bottomRight.lat),
              math.max(topLeft.lat, bottomRight.lat),
              math.min(topLeft.lon, bottomRight.lon),
              math.max(topLeft.lon, bottomRight.lon)
            )
          )
      }
    }
  }

  object GeoBoundingBoxPredicate {
    given geoBoxDecoder: Decoder[GeoBoundingBoxPredicate] = Decoder.instance(c =>
      for {
        field       <- c.downField("field").as[String]
        topLeft     <- c.downField("top_left").as[LatLon]
        bottomRight <- c.downField("bottom_right").as[LatLon]
      } yield {
        GeoBoundingBoxPredicate(field, topLeft, bottomRight)
      }
    )

    given geoBoxEncoder: Encoder[GeoBoundingBoxPredicate] = Encoder.instance(c =>
      Json.obj(
        "field"        -> Json.fromString(c.field),
        "top_left"     -> c.topLeft.asJson,
        "bottom_right" -> c.bottomRight.asJson
      )
    )
  }

  import BoolPredicate.given
  import TermPredicate.given
  import RangePredicate.given

  given predicateEncoder: Encoder[Predicate] = Encoder.instance {
    case bool: BoolPredicate =>
      bool match {
        case and: AndPredicate => json("and", boolPredicateEncoder(bool))
        case or: OrPredicate   => json("or", boolPredicateEncoder(bool))
        case not: NotPredicate => json("not", boolPredicateEncoder(bool))
      }
    case term: TermPredicate             => json("term", termPredicateEncoder(term))
    case range: RangePredicate           => json("range", rangePredicateEncoder(range))
    case geodist: GeoDistancePredicate   => json("geo_distance", geoDistanceEncoder(geodist))
    case geobox: GeoBoundingBoxPredicate => json("geo_box", geoBoxEncoder(geobox))
  }

  given predicateDecoder: Decoder[Predicate] = Decoder.instance(c =>
    c.value.asObject match {
      case Some(obj) =>
        obj.toList match {
          case (field, json) :: Nil =>
            field match {
              case "and"          => json.as[List[Predicate]].map(AndPredicate.apply)
              case "or"           => json.as[List[Predicate]].map(OrPredicate.apply)
              case "not"          => json.as[List[Predicate]].map(NotPredicate.apply)
              case "term"         => termPredicateDecoder.decodeJson(json)
              case "range"        => rangeDecoder.decodeJson(json)
              case "geo_distance" => geoDistanceDecoder.decodeJson(json)
              case "geo_box"      => geoBoxDecoder.decodeJson(json)
              case other          => Left(DecodingFailure(s"filter type '$other' is not supported", c.history))
            }
          case Nil     => Left(DecodingFailure(s"filter predicate should contain a predicate type: $obj", c.history))
          case tooMany => Left(DecodingFailure(s"filter predicate must contain only single value: $tooMany", c.history))
        }
      case None => Left(DecodingFailure(s"filter predicate should be a json object: ${c.value}", c.history))
    }
  )

  def json(field: String, value: JsonObject): Json =
    Json.fromJsonObject(JsonObject.singleton(field, Json.fromJsonObject(value)))

  def json(field: String, value: Json): Json = Json.fromJsonObject(JsonObject.singleton(field, value))
}

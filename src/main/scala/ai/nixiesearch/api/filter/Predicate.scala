package ai.nixiesearch.api.filter

import ai.nixiesearch.config.FieldSchema.{
  FloatFieldSchema,
  IntFieldSchema,
  LongFieldSchema,
  TextFieldSchema,
  TextListFieldSchema
}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Field.TextListField
import ai.nixiesearch.core.FiniteRange.{Higher, Lower}
import ai.nixiesearch.core.{FiniteRange, Logging}
import ai.nixiesearch.core.codec.TextFieldWriter
import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.*
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.{BooleanClause, BooleanQuery, ConstantScoreQuery, TermQuery, Query as LuceneQuery}
import cats.implicits.*
import org.apache.lucene.document.NumericDocValuesField

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

    implicit val boolPredicateEncoder: Encoder[BoolPredicate] = Encoder.instance { bool =>
      Json.fromValues(bool.predicates.map(p => predicateEncoder(p)))
    }

  }
  case class TermPredicate(field: String, value: String) extends Predicate with Logging {
    def compile(mapping: IndexMapping): IO[LuceneQuery] = for {
      _ <- mapping.fields.get(field) match {
        case Some(t: TextFieldSchema) if t.filter     => IO.unit
        case Some(t: TextListFieldSchema) if t.filter => IO.unit
        case Some(schema) if !schema.filter =>
          IO.raiseError(new Exception(s"Cannot filter over a non-filterable field '$field'"))
        case Some(schema) =>
          IO.raiseError(new Exception(s"Term filtering only works with text fields, and field '$field' is $schema"))
        case None => IO.raiseError(new Exception(s"cannot make term "))
      }

    } yield { new TermQuery(new Term(field + TextFieldWriter.RAW_SUFFIX, value)) }

  }

  object TermPredicate {
    implicit val termPredicateEncoder: Encoder[TermPredicate] =
      Encoder.instance(t => Json.fromJsonObject(JsonObject.singleton(t.field, Json.fromString(t.value))))

    implicit val termPredicateDecoder: Decoder[TermPredicate] = Decoder.instance(c =>
      c.value.asObject match {
        case Some(obj) =>
          obj.toList match {
            case ((field, json) :: Nil) =>
              json.asString match {
                case Some(string) => Right(TermPredicate(field, string))
                case None         => Left(DecodingFailure(s"term predicate value should be a string: $json", c.history))
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
        case Some(IntFieldSchema(_, _, _, _, true)) =>
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
        case Some(LongFieldSchema(_, _, _, _, true)) =>
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

        case Some(LongFieldSchema(_, _, _, _, false)) =>
          IO.raiseError(new Exception(s"range query for field '$field' only works with filter=true fields"))
        case Some(IntFieldSchema(_, _, _, _, false)) =>
          IO.raiseError(new Exception(s"range query for field '$field' only works with filter=true fields"))
        case Some(FloatFieldSchema(_, _, _, _, true)) =>
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

        case Some(FloatFieldSchema(_, _, _, _, false)) =>
          IO.raiseError(new Exception(s"range query for field '$field' only works with filter=true fields"))
        case Some(other) => IO.raiseError(new Exception(s"range queries only work with numeric fields: $other"))
        case None        => IO.raiseError(new Exception(s"cannot execute range query over non-existent field $field"))
      }
    }

    implicit val rangeDecoder: Decoder[RangePredicate] = Decoder.instance(c => {
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

    implicit val rangePredicateEncoder: Encoder[RangePredicate] = Encoder.instance {
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

  import BoolPredicate.*
  import TermPredicate.*
  import RangePredicate.*

  implicit val predicateEncoder: Encoder[Predicate] = Encoder.instance {
    case bool: BoolPredicate =>
      bool match {
        case and: AndPredicate => json("and", boolPredicateEncoder(bool))
        case or: OrPredicate   => json("or", boolPredicateEncoder(bool))
        case not: NotPredicate => json("not", boolPredicateEncoder(bool))
      }
    case term: TermPredicate   => json("term", termPredicateEncoder(term))
    case range: RangePredicate => json("range", rangePredicateEncoder(range))
  }

  implicit val predicateDecoder: Decoder[Predicate] = Decoder.instance(c =>
    c.value.asObject match {
      case Some(obj) =>
        obj.toList match {
          case (field, json) :: Nil =>
            field match {
              case "and"   => json.as[List[Predicate]].map(AndPredicate.apply)
              case "or"    => json.as[List[Predicate]].map(OrPredicate.apply)
              case "not"   => json.as[List[Predicate]].map(NotPredicate.apply)
              case "term"  => termPredicateDecoder.decodeJson(json)
              case "range" => rangeDecoder.decodeJson(json)
              case other   => Left(DecodingFailure(s"filter type '$other' is not supported", c.history))
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

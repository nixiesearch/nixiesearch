package ai.nixiesearch.api.filter

import ai.nixiesearch.config.FieldSchema.{FloatFieldSchema, IntFieldSchema, TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Field.TextListField
import ai.nixiesearch.core.Logging
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
    case class RangeGte(field: String, gte: Float) extends RangePredicate {
      override def compile(mapping: IndexMapping): IO[LuceneQuery] =
        build(field, mapping, gte, Int.MaxValue)
    }
    case class RangeLte(field: String, lte: Float) extends RangePredicate {
      override def compile(mapping: IndexMapping): IO[LuceneQuery] =
        build(field, mapping, Int.MinValue, lte)
    }
    case class RangeGteLte(field: String, gte: Float, lte: Float) extends RangePredicate {
      override def compile(mapping: IndexMapping): IO[LuceneQuery] =
        build(field, mapping, gte, lte)
    }

    def build(field: String, mapping: IndexMapping, gte: Float, lte: Float): IO[LuceneQuery] = {
      mapping.fields.get(field) match {
        case Some(IntFieldSchema(_, _, _, _, true)) =>
          IO(org.apache.lucene.document.IntField.newRangeQuery(field, math.round(gte), math.round(lte)))
        case Some(IntFieldSchema(_, _, _, _, false)) =>
          IO.raiseError(new Exception(s"range query for field '$field' only works with filter=true fields"))
        case Some(FloatFieldSchema(_, _, _, _, true)) =>
          IO(org.apache.lucene.document.FloatField.newRangeQuery(field, gte, lte))
        case Some(FloatFieldSchema(_, _, _, _, false)) =>
          IO.raiseError(new Exception(s"range query for field '$field' only works with filter=true fields"))
        case Some(other) => IO.raiseError(new Exception(s"range queries only work with numeric fields: $other"))
        case None        => IO.raiseError(new Exception(s"cannot execute range query over non-existent field $field"))
      }
    }

    case class GteLte(gte: Option[Float], lte: Option[Float])
    implicit val gteLteDecoder: Decoder[GteLte] = deriveDecoder

    implicit val rangeDecoder: Decoder[RangePredicate] = Decoder.instance(c =>
      c.value.asObject match {
        case Some(obj) =>
          obj.toList match {
            case (field, json) :: Nil =>
              gteLteDecoder.decodeJson(json).flatMap {
                case GteLte(Some(gte), Some(lte)) => Right(RangeGteLte(field, gte, lte))
                case GteLte(Some(gte), None)      => Right(RangeGte(field, gte))
                case GteLte(None, Some(lte))      => Right(RangeLte(field, lte))
                case GteLte(None, None) =>
                  Left(DecodingFailure(s"range expects at least one gte or lte field, but got none", c.history))
              }
            case Nil => Left(DecodingFailure(s"range should contain a field, but it is empty: $obj", c.history))
            case other =>
              Left(DecodingFailure(s"range should contain a single field, but it has many: $obj", c.history))
          }
        case None => Left(DecodingFailure("range should be a json object", c.history))
      }
    )

    implicit val rangePredicateEncoder: Encoder[RangePredicate] = Encoder.instance {
      case RangeGte(field, gte) => json(field, JsonObject.singleton("gte", Json.fromDoubleOrNull(gte)))
      case RangeLte(field, lte) => json(field, JsonObject.singleton("lte", Json.fromDoubleOrNull(lte)))
      case RangeGteLte(field, gte, lte) =>
        json(
          field,
          JsonObject.fromIterable(
            List(
              "gte" -> Json.fromDoubleOrNull(gte),
              "lte" -> Json.fromDoubleOrNull(lte)
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

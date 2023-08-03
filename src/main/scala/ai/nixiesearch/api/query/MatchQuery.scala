package ai.nixiesearch.api.query

import ai.nixiesearch.api.query.MatchQuery.Operator
import ai.nixiesearch.api.query.MatchQuery.Operator.OR
import ai.nixiesearch.config.FieldSchema.{TextFieldSchema, TextListFieldSchema}
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.core.Logging
import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import org.apache.lucene.search.{BooleanClause, BooleanQuery, TermQuery, Query as LuceneQuery}
import io.circe.generic.semiauto.*
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause.Occur

import scala.util.{Failure, Success}

case class MatchQuery(field: String, query: String, operator: Operator = OR) extends Query with Logging {
  override def compile(mapping: IndexMapping): IO[LuceneQuery] = for {
    builder <- IO.pure(new BooleanQuery.Builder())
    terms   <- MatchQuery.analyze(mapping, field, query)
    _ <- IO(terms.foreach(term => builder.add(new BooleanClause(new TermQuery(new Term(field, term)), operator.occur))))
    query <- IO(builder.build())
    _     <- debug(s"query: $query")
  } yield {
    query
  }
}

object MatchQuery {
  sealed trait Operator {
    def occur: BooleanClause.Occur
  }
  object Operator {
    case object AND extends Operator {
      val occur = Occur.MUST
    }
    case object OR extends Operator {
      val occur = Occur.SHOULD
    }

    implicit val operatorDecoder: Decoder[Operator] = Decoder.decodeString.emapTry {
      case "and" | "AND" => Success(AND)
      case "or" | "OR"   => Success(OR)
      case other         => Failure(new Exception(s"cannot parse operator '$other', use AND|OR"))
    }

    implicit val operatorEncoder: Encoder[Operator] = Encoder.encodeString.contramap {
      case AND => "and"
      case OR  => "or"
    }
  }

  case class FieldQuery(query: String)

  implicit val fieldQueryDecoder: Decoder[FieldQuery] = deriveDecoder
  implicit val fieldQueryEncoder: Encoder[FieldQuery] = deriveEncoder

  implicit val matchQueryEncoder: Encoder[MatchQuery] = Encoder.instance(q =>
    Json.fromJsonObject(JsonObject.fromIterable(List(q.field -> fieldQueryEncoder(FieldQuery(q.query)))))
  )
  implicit val matchQueryDecoder: Decoder[MatchQuery] = Decoder.instance(c =>
    c.as[Map[String, FieldQuery]].map(_.toList) match {
      case Left(_) =>
        c.as[Map[String, String]].map(_.toList) match {
          case Left(error)                  => Left(error)
          case Right((field, query) :: Nil) => Right(MatchQuery(field, query, OR))
          case Right(other)                 => Left(DecodingFailure(s"cannot decode query $other", c.history))
        }
      case Right((field, query) :: Nil) => Right(MatchQuery(field, query.query, OR))
      case Right(other)                 => Left(DecodingFailure(s"cannot decode query $other", c.history))
    }
  )

  def analyze(mapping: IndexMapping, field: String, query: String): IO[List[String]] = mapping.fields.get(field) match {
    case Some(TextFieldSchema(_, LexicalSearch(lang), _, _, _, _))     => IO(lang.analyze(query))
    case Some(TextListFieldSchema(_, LexicalSearch(lang), _, _, _, _)) => IO(lang.analyze(query))
    case Some(other) => IO.raiseError(new Exception(s"Cannot search over a non-text field '$field'"))
    case None        => IO.raiseError(new Exception(s"Cannot search over a non-existent field '$field'"))
  }

}

package ai.nixiesearch.api.query.retrieve

import MatchQuery.Operator
import MatchQuery.Operator.OR
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.config.FieldSchema.TextLikeFieldSchema
import ai.nixiesearch.config.mapping.{IndexMapping, Language}
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.core.suggest.AnalyzedIterator
import cats.effect.IO
import io.circe.generic.semiauto.*
import io.circe.*
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.Term
import org.apache.lucene.search
import org.apache.lucene.search.{BooleanClause, BooleanQuery, TermQuery, Query as LuceneQuery}
import org.apache.lucene.search.BooleanClause.Occur

import scala.util.{Failure, Success}

case class MatchQuery(field: String, query: String, operator: Operator = OR) extends RetrieveQuery {
  override def compile(
      mapping: IndexMapping,
      filter: Option[Filters],
      encoders: EmbedModelDict,
      fields: List[String]
  ): IO[search.Query] =
    for {
      schema <- IO.fromOption(mapping.fieldSchema(field))(UserError(s"field '$field' not found in index mapping"))
      _      <- schema match {
        case t: TextLikeFieldSchema[?] if t.search.lexical.nonEmpty => IO.unit
        case t: TextLikeFieldSchema[?]                              =>
          IO.raiseError(UserError(s"field '$field' is not lexically searchable, check the index mapping"))
        case other => IO.raiseError(UserError(s"field '$field' is not a text field"))
      }
      analyzer <- IO(mapping.analyzer.getWrappedAnalyzer(field))
      builder  <- IO.pure(new BooleanQuery.Builder())
      _        <- IO(
        AnalyzedIterator(analyzer, field, query).foreach(term =>
          builder.add(new BooleanClause(new TermQuery(new Term(field, term)), operator.occur))
        )
      )
      result <- applyFilters(mapping, builder.build(), filter)
    } yield {
      result
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

  case class FieldQuery(query: String, operator: Option[Operator])

  implicit val fieldQueryDecoder: Decoder[FieldQuery] = deriveDecoder
  implicit val fieldQueryEncoder: Encoder[FieldQuery] = deriveEncoder

  implicit val matchQueryEncoder: Encoder[MatchQuery] = Encoder.instance(q =>
    Json.fromJsonObject(
      JsonObject.fromIterable(List(q.field -> fieldQueryEncoder(FieldQuery(q.query, Some(q.operator)))))
    )
  )
  implicit val matchQueryDecoder: Decoder[MatchQuery] = Decoder.instance(c =>
    c.as[Map[String, FieldQuery]].map(_.toList) match {
      case Left(_) =>
        c.as[Map[String, String]].map(_.toList) match {
          case Left(error)                  => Left(error)
          case Right((field, query) :: Nil) => Right(MatchQuery(field, query, OR))
          case Right(other)                 => Left(DecodingFailure(s"cannot decode query $other", c.history))
        }
      case Right((field, query) :: Nil) => Right(MatchQuery(field, query.query, query.operator.getOrElse(OR)))
      case Right(other)                 => Left(DecodingFailure(s"cannot decode query $other", c.history))
    }
  )

}

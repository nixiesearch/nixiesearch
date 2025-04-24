package ai.nixiesearch.api.query.retrieve

import MatchQuery.Operator
import MatchQuery.Operator.OR
import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.api.query.Query
import ai.nixiesearch.config.mapping.{IndexMapping, Language}
import ai.nixiesearch.core.Logging
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
  override def compile(mapping: IndexMapping, filter: Option[Filters]): IO[search.Query] = {
    val analyzer = mapping.analyzer.getWrappedAnalyzer(field)
    filter match {
      case None =>
        IO(fieldQuery(field, query, analyzer, operator.occur))
      case Some(f) =>
        f.toLuceneQuery(mapping).flatMap {
          case Some(filterQuery) =>
            IO {
              val outerQuery = new BooleanQuery.Builder()
              outerQuery.add(new BooleanClause(filterQuery, Occur.FILTER))
              outerQuery.add(new BooleanClause(fieldQuery(field, query, analyzer, operator.occur), Occur.MUST))
              outerQuery.build()
            }
          case None =>
            IO(fieldQuery(field, query, analyzer, operator.occur))
        }
    }

  }
  private def fieldQuery(field: String, query: String, analyzer: Analyzer, occur: Occur): LuceneQuery = {
    val fieldQuery = new BooleanQuery.Builder()
    AnalyzedIterator(analyzer, field, query)
      .foreach(term => fieldQuery.add(new BooleanClause(new TermQuery(new Term(field, term)), occur)))
    fieldQuery.build()
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

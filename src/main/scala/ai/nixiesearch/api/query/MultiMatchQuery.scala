package ai.nixiesearch.api.query

import ai.nixiesearch.api.{SearchRoute, query}
import ai.nixiesearch.api.query.MatchQuery.Operator
import ai.nixiesearch.api.query.MatchQuery.Operator.OR
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index
import cats.effect.IO
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.*
import org.apache.lucene.search.{BooleanClause, BooleanQuery, IndexSearcher, TermQuery, Query as LuceneQuery}
import cats.implicits.*
import org.apache.lucene.index.{IndexReader, Term}

case class MultiMatchQuery(query: String, fields: List[String], operator: Operator = OR) extends Query {
  override def search(request: SearchRoute.SearchRequest, reader: index.IndexReader): IO[SearchRoute.SearchResponse] =
    ???
//  override def toLuceneQuery(mapping: IndexMapping): IO[LuceneQuery] = for {
//    builder <- IO.pure(new BooleanQuery.Builder())
//    _ <- fields.traverse(field =>
//      for {
//        // TODO: add optimization for same lang with multi fields
//        fieldBuilder <- IO.pure(new BooleanQuery.Builder())
//        terms        <- MatchQuery.analyze(mapping, field, query)
//        _ <- IO(
//          terms.foreach(term =>
//            fieldBuilder.add(new BooleanClause(new TermQuery(new Term(field, term)), operator.occur))
//          )
//        )
//      } yield {
//        builder.add(fieldBuilder.build(), operator.occur)
//      }
//    )
//    query <- IO(builder.build())
//    _     <- debug(s"query: $query")
//  } yield {
//    query
//  }
}

object MultiMatchQuery {
  implicit val multiMatchQueryEncoder: Encoder[MultiMatchQuery] = deriveEncoder

  implicit val multiMatchQueryDecoder: Decoder[MultiMatchQuery] = Decoder
    .instance(c =>
      for {
        query  <- c.downField("query").as[String]
        fields <- c.downField("fields").as[List[String]]
        op     <- c.downField("operator").as[Option[Operator]].map(_.getOrElse(Operator.OR))
      } yield {
        MultiMatchQuery(query, fields, op)
      }
    )
    .ensure(_.query.nonEmpty, "query cannot be empty")
    .ensure(_.fields.nonEmpty, "list of fields cannot be empty")
}

package ai.nixiesearch.api.query

import ai.nixiesearch.api.SearchRoute.SearchRequest
import ai.nixiesearch.api.{SearchRoute, query}
import ai.nixiesearch.api.query.MatchQuery.Operator
import ai.nixiesearch.api.query.MatchQuery.Operator.OR
import ai.nixiesearch.config.FieldSchema.TextLikeFieldSchema
import ai.nixiesearch.config.mapping.SearchType.LexicalSearch
import ai.nixiesearch.config.mapping.{IndexMapping, Language, SearchType}
import ai.nixiesearch.core.nn.model.BiEncoderCache
import ai.nixiesearch.index
import cats.effect.IO
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.*
import org.apache.lucene.search.{BooleanClause, BooleanQuery, IndexSearcher, TermQuery, TopDocs, Query as LuceneQuery}
import cats.implicits.*
import org.apache.lucene.facet.FacetsCollector
import org.apache.lucene.index.{IndexReader, Term}

case class MultiMatchQuery(query: String, fields: List[String], operator: Operator = OR) extends Query

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

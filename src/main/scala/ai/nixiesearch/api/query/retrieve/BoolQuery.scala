package ai.nixiesearch.api.query.retrieve

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import cats.effect.IO
import org.apache.lucene.search.{BooleanClause, BooleanQuery, Query}
import io.circe.{Codec, Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.*
import cats.syntax.all.*
import org.apache.lucene.search.BooleanClause.Occur

import scala.jdk.CollectionConverters.*

case class BoolQuery(
    should: List[RetrieveQuery] = Nil,
    must: List[RetrieveQuery] = Nil,
    must_not: List[RetrieveQuery] = Nil
) extends RetrieveQuery {
  override def compile(mapping: IndexMapping, maybeFilter: Option[Filters], encoders: EmbedModelDict): IO[Query] = for {
    builder <- IO.pure(new BooleanQuery.Builder())
    _ <- should.traverse(
      _.compile(mapping, maybeFilter, encoders).map(q => builder.add(new BooleanClause(q, Occur.SHOULD)))
    )
    _ <- must.traverse(
      _.compile(mapping, maybeFilter, encoders).map(q => builder.add(new BooleanClause(q, Occur.MUST)))
    )
    _ <- must_not.traverse(
      _.compile(mapping, maybeFilter, encoders).map(q => builder.add(new BooleanClause(q, Occur.MUST_NOT)))
    )
    result <- applyFilters(mapping, builder.build(), maybeFilter)
  } yield {
    result
  }
}

object BoolQuery {
  given boolQueryEncoder: Encoder[BoolQuery] = deriveEncoder
  given boolQueryDecoder: Decoder[BoolQuery] = Decoder.instance(c =>
    for {
      should  <- c.downField("should").as[Option[List[RetrieveQuery]]]
      must    <- c.downField("must").as[Option[List[RetrieveQuery]]]
      mustNot <- c.downField("must_not").as[Option[List[RetrieveQuery]]]
      _ <- (should, must, mustNot) match {
        case (None, None, None) => Left(DecodingFailure("bool query should have at least single predicate", c.history))
        case (Some(Nil), Some(Nil), Some(Nil)) =>
          Left(DecodingFailure("bool query should have at least single predicate", c.history))
        case _ => Right(0)
      }
    } yield {
      BoolQuery(should.getOrElse(Nil), must.getOrElse(Nil), mustNot.getOrElse(Nil))
    }
  )
}

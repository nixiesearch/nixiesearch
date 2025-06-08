package ai.nixiesearch.api.query.retrieve

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import cats.effect.IO
import org.apache.lucene.search.{DisjunctionMaxQuery, Query}
import io.circe.{Codec, Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.*
import cats.syntax.all.*

import scala.jdk.CollectionConverters.*

case class DisMaxQuery(queries: List[RetrieveQuery], tie_breaker: Float = 0.0) extends RetrieveQuery {
  override def compile(
      mapping: IndexMapping,
      maybeFilter: Option[Filters],
      encoders: EmbedModelDict,
      fields: List[String]
  ): IO[Query] = for {
    luceneQueries <- queries.traverse(_.compile(mapping, maybeFilter, encoders, fields))
    result <- applyFilters(mapping, new DisjunctionMaxQuery(luceneQueries.asJavaCollection, tie_breaker), maybeFilter)
  } yield {
    result
  }
}

object DisMaxQuery {
  given disMaxQueryEncoder: Encoder[DisMaxQuery] = deriveEncoder
  given disMaxQueryDecoder: Decoder[DisMaxQuery] = Decoder.instance(c =>
    for {
      queries <- c.downField("queries").as[List[RetrieveQuery]]
      _       <- queries match {
        case q1 :: q2 :: tail => Right(queries)
        case _ => Left(DecodingFailure(s"queries array must have 2+ queries, but got ${queries.length}", c.history))
      }
      tieBreaker <- c.downField("tie_breaker").as[Option[Float]]
    } yield {
      DisMaxQuery(queries, tieBreaker.getOrElse(0.0))
    }
  )
}

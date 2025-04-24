package ai.nixiesearch.api.query.retrieve

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import cats.effect.IO
import org.apache.lucene.search.Query
import io.circe.generic.semiauto.*
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json}

sealed trait MultiMatchQuery extends RetrieveQuery {
  def query: String
  def fields: List[String]
}

object MultiMatchQuery {
  case class BestFieldsQuery(query: String, fields: List[String], tie_breaker: Option[Float] = None)
      extends MultiMatchQuery {
    override def compile(mapping: IndexMapping, maybeFilter: Option[Filters], encoders: EmbedModelDict): IO[Query] =
      DisMaxQuery(queries = fields.map(field => MatchQuery(field, query)), tie_breaker.getOrElse(0.0))
        .compile(mapping, maybeFilter, encoders)
  }
  case class MostFieldsQuery(query: String, fields: List[String]) extends MultiMatchQuery {
    override def compile(mapping: IndexMapping, maybeFilter: Option[Filters], encoders: EmbedModelDict): IO[Query] =
      BoolQuery(should = fields.map(field => MatchQuery(field, query))).compile(mapping, maybeFilter, encoders)
  }

  given bestFieldsQueryCodec: Codec[BestFieldsQuery] = deriveCodec
  given mostFieldsQueryCodec: Codec[MostFieldsQuery] = deriveCodec
  given multiMatchQueryEncoder: Encoder[MultiMatchQuery] = Encoder.instance {
    case q: BestFieldsQuery => bestFieldsQueryCodec(q).deepMerge(tpe("best_fields"))
    case q: MostFieldsQuery => mostFieldsQueryCodec(q).deepMerge(tpe("most_fields"))
  }
  given multiMatchQueryDecoder: Decoder[MultiMatchQuery] = Decoder.instance(c =>
    c.downField("type").as[Option[String]] match {
      case Right(Some("best_fields")) => bestFieldsQueryCodec.tryDecode(c)
      case Right(Some("most_fields")) => mostFieldsQueryCodec.tryDecode(c)
      case Right(Some(other))         => Left(DecodingFailure(s"multi_match type '$other' not supported", c.history))
      case Right(None)                => bestFieldsQueryCodec.tryDecode(c)
      case Left(err)                  => Left(err)
    }
  )

  def tpe(name: String) = Json.obj("type" -> Json.fromString(name))

}

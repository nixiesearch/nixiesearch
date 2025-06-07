package ai.nixiesearch.api.query.retrieve

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.config.mapping.{FieldName, IndexMapping}
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import cats.effect.IO
import org.apache.lucene.search.Query
import io.circe.generic.semiauto.*
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json}

sealed trait MultiMatchQuery extends RetrieveQuery {
  def query: String
  def fields: List[FieldName]
}

object MultiMatchQuery {
  case class BestFieldsQuery(query: String, fields: List[FieldName], tie_breaker: Option[Float] = None)
      extends MultiMatchQuery {
    override def compile(
        mapping: IndexMapping,
        maybeFilter: Option[Filters],
        encoders: EmbedModelDict,
        allfields: List[String]
    ): IO[Query] = {
      val expandedFields = expandFields(fields, allfields.toSet)
      DisMaxQuery(queries = expandedFields.map(field => MatchQuery(field, query)), tie_breaker.getOrElse(0.0f))
        .compile(mapping, maybeFilter, encoders, allfields)
    }
  }
  case class MostFieldsQuery(query: String, fields: List[FieldName]) extends MultiMatchQuery {
    override def compile(
        mapping: IndexMapping,
        maybeFilter: Option[Filters],
        encoders: EmbedModelDict,
        allfields: List[String]
    ): IO[Query] = {
      val expandedFields = expandFields(fields, allfields.toSet)
      BoolQuery(should = expandedFields.map(field => MatchQuery(field, query)))
        .compile(mapping, maybeFilter, encoders, allfields)
    }
  }

  given bestFieldsQueryCodec: Codec[BestFieldsQuery]     = deriveCodec
  given mostFieldsQueryCodec: Codec[MostFieldsQuery]     = deriveCodec
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

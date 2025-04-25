package ai.nixiesearch.api.query.retrieve

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.config.FieldSchema.TextLikeFieldSchema
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import cats.effect.IO
import org.apache.lucene.search.Query
import io.circe.{Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.*

case class SemanticQuery(field: String, query: String, k: Option[Int] = None) extends RetrieveQuery {
  override def compile(
      mapping: IndexMapping,
      maybeFilter: Option[Filters],
      encoders: EmbedModelDict,
      fields: List[String]
  ): IO[Query] = for {
    schema <- IO
      .fromOption(mapping.fieldSchemaOf[TextLikeFieldSchema[?]](field))(UserError(s"no mapping for field $field"))
    semantic       <- IO.fromOption(schema.search.semantic)(UserError(s"field $field search type is not semantic"))
    queryEmbedding <- encoders.encode(semantic.model, TaskType.Query, query)
    result         <- KnnQuery(field, queryEmbedding, k).compile(mapping, maybeFilter, encoders, fields)
  } yield {
    result
  }
}

object SemanticQuery {
  given semanticQueryEncoder: Encoder[SemanticQuery] = deriveEncoder
  given semanticQueryDecoder: Decoder[SemanticQuery] = Decoder.instance(c =>
    c.value.asObject match {
      case Some(obj) =>
        obj.keys.toList match {
          case list if list.contains("field") =>
            for {
              field <- c.downField("field").as[String]
              query <- c.downField("query").as[String]
              k     <- c.downField("k").as[Option[Int]]
            } yield {
              SemanticQuery(field, query, k)
            }
          case head :: Nil =>
            for {
              value <- c.downField(head).as[String]
            } yield {
              SemanticQuery(head, value)
            }
          case other => Left(DecodingFailure(s"cannot decode semantic query", c.history))
        }
      case None => Left(DecodingFailure(s"Semantic query should be a JSON object", c.history))
    }
  )
}

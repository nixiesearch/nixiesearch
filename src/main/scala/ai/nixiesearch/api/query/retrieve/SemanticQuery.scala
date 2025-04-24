package ai.nixiesearch.api.query.retrieve

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.config.FieldSchema.TextLikeFieldSchema
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.core.Error.UserError
import ai.nixiesearch.core.nn.model.embedding.EmbedModel.TaskType
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import cats.effect.IO
import org.apache.lucene.search.Query
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

case class SemanticQuery(field: String, query: String, k: Int = 10, num_candidates: Int = 10) extends RetrieveQuery {
  override def compile(mapping: IndexMapping, maybeFilter: Option[Filters], encoders: EmbedModelDict): IO[Query] = for {
    schema <- IO
      .fromOption(mapping.fieldSchemaOf[TextLikeFieldSchema[?]](field))(UserError(s"no mapping for field $field"))
    semantic       <- IO.fromOption(schema.search.semantic)(UserError(s"field $field search type is not semantic"))
    queryEmbedding <- encoders.encode(semantic.model, TaskType.Query, query)
    result         <- KnnQuery(field, queryEmbedding, k, num_candidates).compile(mapping, maybeFilter, encoders)
  } yield {
    result
  }
}

object SemanticQuery {
  given semanticQueryEncoder: Encoder[SemanticQuery] = deriveEncoder
  given semanticQueryDecoder: Decoder[SemanticQuery] = Decoder.instance(c =>
    for {
      field         <- c.downField("field").as[String]
      query         <- c.downField("query").as[String]
      k             <- c.downField("k").as[Option[Int]]
      numCandidates <- c.downField("num_candidates").as[Option[Int]]
    } yield {
      SemanticQuery(field, query, k.getOrElse(10), numCandidates.getOrElse(10))
    }
  )
}

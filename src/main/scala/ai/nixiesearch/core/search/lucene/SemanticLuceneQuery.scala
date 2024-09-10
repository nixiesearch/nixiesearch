package ai.nixiesearch.core.search.lucene

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.config.InferenceConfig.PromptConfig
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.ModelPrefix
import ai.nixiesearch.core.nn.{ModelHandle, ModelRef}
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import cats.effect.IO
import org.apache.lucene.search.{KnnFloatVectorQuery, Query as LuceneQuery}

object SemanticLuceneQuery {
  def create(
      encoders: EmbedModelDict,
      model: ModelRef,
      query: String,
      field: String,
      size: Int,
      filter: Option[Filters],
      mapping: IndexMapping
  ): IO[List[LuceneQuery]] = for {
    queryEmbed <- encoders.encodeQuery(model, query)
    filterOption <- filter match {
      case Some(f) => f.toLuceneQuery(mapping)
      case None    => IO.none

    }
    query <- filterOption match {
      case Some(filter) => IO(new KnnFloatVectorQuery(field, queryEmbed, size, filter))
      case None         => IO(new KnnFloatVectorQuery(field, queryEmbed, size))
    }
  } yield {
    List(query)
  }
}

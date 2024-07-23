package ai.nixiesearch.core.search.lucene

import ai.nixiesearch.api.filter.Filters
import ai.nixiesearch.config.mapping.IndexMapping
import ai.nixiesearch.config.mapping.SearchType.ModelPrefix
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.model.embedding.EmbedderDict
import cats.effect.IO
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.{BooleanClause, BooleanQuery, KnnFloatVectorQuery, TermQuery, Query as LuceneQuery}

object SemanticLuceneQuery {
  def create(
              encoders: EmbedderDict,
              model: ModelHandle,
              prefix: ModelPrefix,
              query: String,
              field: String,
              size: Int,
              filter: Option[Filters],
              mapping: IndexMapping
  ): IO[List[LuceneQuery]] = for {
    queryEmbed <- encoders.encode(model, prefix.query + query)
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

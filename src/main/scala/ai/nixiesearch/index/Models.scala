package ai.nixiesearch.index

import ai.nixiesearch.config.CacheConfig
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import cats.effect.IO
import cats.effect.kernel.Resource

case class Models(embedding: EmbedModelDict)

object Models {
  def create(
      embeddingHandles: List[ModelHandle],
      generativeHandles: List[ModelHandle],
      cacheConfig: CacheConfig
  ): Resource[IO, Models] = for {
    embeddings <- EmbedModelDict.create(embeddingHandles, cacheConfig)
  } yield {
    Models(embeddings)
  }
}

package ai.nixiesearch.index

import ai.nixiesearch.config.CacheConfig
import ai.nixiesearch.config.mapping.RAGConfig.RAGModelConfig
import ai.nixiesearch.core.nn.ModelHandle
import ai.nixiesearch.core.nn.model.ModelFileCache
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.core.nn.model.generative.GenerativeModelDict
import cats.effect.IO
import cats.effect.kernel.Resource

import java.nio.file.Paths

case class Models(embedding: EmbedModelDict, generative: GenerativeModelDict)

object Models {
  def create(
      embeddingHandles: List[ModelHandle],
      generativeHandles: List[RAGModelConfig],
      cacheConfig: CacheConfig
  ): Resource[IO, Models] = for {
    cache      <- Resource.eval(ModelFileCache.create(Paths.get(cacheConfig.dir)))
    embeddings <- EmbedModelDict.create(embeddingHandles, cache)
    generative <- GenerativeModelDict.create(generativeHandles, cache)
  } yield {
    Models(embeddings, generative)
  }
}

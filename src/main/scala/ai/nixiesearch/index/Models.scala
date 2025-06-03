package ai.nixiesearch.index

import ai.nixiesearch.config.{CacheConfig, InferenceConfig}
import ai.nixiesearch.core.nn.huggingface.ModelFileCache
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.core.nn.model.generative.GenerativeModelDict
import cats.effect.IO
import cats.effect.kernel.Resource

import java.nio.file.Paths

case class Models(embedding: EmbedModelDict, generative: GenerativeModelDict)

object Models {
  def create(
      inferenceConfig: InferenceConfig,
      cacheConfig: CacheConfig
  ): Resource[IO, Models] = for {
    cache      <- Resource.eval(ModelFileCache.create(Paths.get(cacheConfig.dir)))
    embeddings <- EmbedModelDict.create(inferenceConfig.embedding, cache)
    generative <- GenerativeModelDict.create(inferenceConfig.completion, cache)
  } yield {
    Models(embeddings, generative)
  }

}

package ai.nixiesearch.index

import ai.nixiesearch.config.{CacheConfig, InferenceConfig}
import ai.nixiesearch.core.metrics.Metrics
import ai.nixiesearch.core.nn.huggingface.ModelFileCache
import ai.nixiesearch.core.nn.model.embedding.EmbedModelDict
import ai.nixiesearch.core.nn.model.generative.GenerativeModelDict
import ai.nixiesearch.core.nn.model.ranking.RankModelDict
import cats.effect.IO
import cats.effect.kernel.Resource

import java.nio.file.Paths

case class Models(embedding: EmbedModelDict, generative: GenerativeModelDict, ranker: RankModelDict)

object Models {
  def create(
      inferenceConfig: InferenceConfig,
      cacheConfig: CacheConfig,
      metrics: Metrics
  ): Resource[IO, Models] = for {
    cache      <- Resource.eval(ModelFileCache.create(Paths.get(cacheConfig.dir)))
    embeddings <- EmbedModelDict.create(inferenceConfig.embedding, cache, metrics)
    generative <- GenerativeModelDict.create(inferenceConfig.completion, cache, metrics)
    ranker     <- RankModelDict.create(inferenceConfig.ranker, cache, metrics)
  } yield {
    Models(embeddings, generative, ranker)
  }

}

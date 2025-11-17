package ai.nixiesearch.core.nn.model.ranking

import ai.nixiesearch.config.InferenceConfig.RankInferenceModelConfig
import ai.nixiesearch.core.Error.{BackendError, UserError}
import ai.nixiesearch.core.Logging
import ai.nixiesearch.core.metrics.Metrics
import ai.nixiesearch.core.nn.ModelRef
import ai.nixiesearch.core.nn.huggingface.ModelFileCache
import ai.nixiesearch.core.nn.model.ranking.providers.OnnxRankModel
import ai.nixiesearch.core.nn.model.ranking.providers.OnnxRankModel.OnnxRankInferenceModelConfig
import ai.nixiesearch.util.Version
import cats.effect.{IO, Resource}
import cats.syntax.all.*

case class RankModelDict(rankers: Map[ModelRef, RankModel], metrics: Metrics) extends Logging {
  def score(model: ModelRef, query: String, docs: List[String]): IO[List[Float]] =
    IO.fromOption(rankers.get(model))(UserError(s"model $model not defined in mapping")).flatMap { ranker =>
      for {
        _      <- IO(metrics.inference.rankTotal.labelValues(model.name).inc())
        _      <- IO(metrics.inference.rankDocTotal.labelValues(model.name).inc(docs.size))
        start  <- IO(System.currentTimeMillis())
        result <- ranker.score(query, docs)
        finish <- IO(System.currentTimeMillis())
        _      <- IO(metrics.inference.rankTimeSeconds.labelValues(model.name).inc((finish - start) / 1000.0))
      } yield result
    }
}

object RankModelDict extends Logging {
  def create(
      models: Map[ModelRef, RankInferenceModelConfig],
      localFileCache: ModelFileCache,
      metrics: Metrics
  ): Resource[IO, RankModelDict] = for {
    rankers <- models.toList.map {
      case (model, config: OnnxRankInferenceModelConfig) if Version.isGraalVMNativeImage =>
        Resource.eval(IO.raiseError(UserError(s"$model is not supported on native docker images, please use JDK one")))
      case (model, config: OnnxRankInferenceModelConfig) =>
        OnnxRankModel.create(config.model, config, localFileCache).map(m => model -> m)
      case (model, other) =>
        Resource.eval(IO.raiseError(BackendError(s"do not know how to load $model, this is a bug!")))
    }.sequence
  } yield {
    RankModelDict(rankers.toMap, metrics)
  }

}

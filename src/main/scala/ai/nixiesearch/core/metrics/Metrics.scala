package ai.nixiesearch.core.metrics

import io.prometheus.metrics.model.registry.PrometheusRegistry

case class Metrics(registry: PrometheusRegistry) {
  val system    = SystemMetrics(registry)
  val search    = SearchMetrics(registry)
  val inference = InferenceMetrics(registry)
  val indexer   = IndexerMetrics(registry)
}

object Metrics {
  def apply() = {
    new Metrics(new PrometheusRegistry())
  }
}

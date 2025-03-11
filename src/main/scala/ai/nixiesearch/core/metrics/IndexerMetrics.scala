package ai.nixiesearch.core.metrics

import io.prometheus.metrics.core.metrics.{Counter, Gauge}
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.Labels

case class IndexerMetrics(registry: PrometheusRegistry) {

  val flushTotal = Counter
    .builder()
    .name("nixiesearch_index_flush_total")
    .help("Total number of suggest queries")
    .labelNames("index")
    .register(registry)

  val flushTimeSeconds = Gauge
    .builder()
    .name("nixiesearch_index_flush_time_seconds")
    .help("Cumulative flush time in seconds")
    .labelNames("index")
    .register(registry)
}

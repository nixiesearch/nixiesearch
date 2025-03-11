package ai.nixiesearch.core.metrics

import io.prometheus.metrics.core.metrics.{Counter, Gauge}
import io.prometheus.metrics.model.snapshots.Labels

case class IndexerMetrics(indexName: String) {
  val labels = Labels.of("index", indexName)



  val flushTotal = Counter
    .builder()
    .name("nixiesearch_index_flush_total")
    .help("Total number of suggest queries")
    .constLabels(labels)
    .register()

  val flushTimeSeconds = Gauge
    .builder()
    .name("nixiesearch_index_flush_time_seconds")
    .help("Cumulative flush time in seconds")
    .constLabels(labels)
    .register()
}

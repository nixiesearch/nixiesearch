package ai.nixiesearch.core.metrics

import io.prometheus.metrics.core.metrics.{Counter, Gauge}
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.Labels

case class SearchMetrics(registry: PrometheusRegistry) {

  val docs = Gauge
    .builder()
    .name("nixiesearch_index_docs")
    .help("Count of documents in this index")
    .labelNames("index")
    .register(registry)

  val activeQueries = Gauge
    .builder()
    .name("nixiesearch_search_active_queries")
    .help("The number of currently active queries")
    .labelNames("index")
    .register(registry)

  val searchTotal = Counter
    .builder()
    .name("nixiesearch_search_query_total")
    .help("Total number of search queries")
    .labelNames("index")
    .register(registry)

  val searchTimeSeconds = Gauge
    .builder()
    .name("nixiesearch_search_query_time_seconds")
    .help("Total search query time in seconds")
    .labelNames("index")
    .register(registry)

  val suggestTotal = Counter
    .builder()
    .name("nixiesearch_suggest_query_total")
    .help("Total number of suggest queries")
    .labelNames("index")
    .register(registry)

  val suggestTimeSeconds = Gauge
    .builder()
    .name("nixiesearch_suggest_query_time_seconds")
    .help("Total suggest query time in seconds")
    .labelNames("index")
    .register(registry)

  val ragTotal = Counter
    .builder()
    .name("nixiesearch_rag_query_total")
    .help("Total number of suggest queries")
    .labelNames("index")
    .register(registry)

  val ragTimeSeconds = Gauge
    .builder()
    .name("nixiesearch_rag_query_time_seconds")
    .help("Total suggest query time in seconds")
    .labelNames("index")
    .register(registry)

}

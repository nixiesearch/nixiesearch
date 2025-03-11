package ai.nixiesearch.core.metrics

import io.prometheus.metrics.core.metrics.{Counter, Gauge}
import io.prometheus.metrics.model.snapshots.Labels

case class SearchMetrics(indexName: String) {
  val labels = Labels.of("index", indexName)

  val docs = Gauge
    .builder()
    .name("nixiesearch_index_docs")
    .help("Count of documents in this index")
    .constLabels(labels)
    .register()

  val activeQueries = Gauge
    .builder()
    .name("nixiesearch_search_active_queries")
    .help("The number of currently active queries")
    .constLabels(labels)
    .register()

  val searchTotal = Counter
    .builder()
    .name("nixiesearch_search_query_total")
    .help("Total number of search queries")
    .constLabels(labels)
    .register()

  val searchTimeSeconds = Gauge
    .builder()
    .name("nixiesearch_search_query_time_seconds")
    .help("Total search query time in seconds")
    .constLabels(labels)
    .register()

  val suggestTotal = Counter
    .builder()
    .name("nixiesearch_suggest_query_total")
    .help("Total number of suggest queries")
    .constLabels(labels)
    .register()

  val suggestTimeSeconds = Gauge
    .builder()
    .name("nixiesearch_suggest_query_time_seconds")
    .help("Total suggest query time in seconds")
    .constLabels(labels)
    .register()

  val ragTotal = Counter
    .builder()
    .name("nixiesearch_rag_query_total")
    .help("Total number of suggest queries")
    .constLabels(labels)
    .register()

  val ragTimeSeconds = Gauge
    .builder()
    .name("nixiesearch_rag_query_time_seconds")
    .help("Total suggest query time in seconds")
    .constLabels(labels)
    .register()

}

package ai.nixiesearch.core.metrics

import io.prometheus.metrics.core.metrics.{Counter, Gauge}
import io.prometheus.metrics.model.registry.PrometheusRegistry

case class InferenceMetrics(registry: PrometheusRegistry) {
  val embedTotal = Counter
    .builder()
    .name("nixiesearch_inference_embedding_request_total")
    .help("Total number of embedding queries")
    .labelNames("model")
    .register(registry)

  val embedDocTotal = Counter
    .builder()
    .name("nixiesearch_inference_embedding_doc_total")
    .help("Total number of embedded docs")
    .labelNames("model")
    .register(registry)

  val embedTimeSeconds = Gauge
    .builder()
    .name("nixiesearch_inference_embedding_request_time_seconds")
    .help("Total embed query time in seconds")
    .labelNames("model")
    .register(registry)

  val completionTotal = Counter
    .builder()
    .name("nixiesearch_inference_completion_request_total")
    .help("Total number of embedding queries")
    .labelNames("model")
    .register(registry)

  val completionGeneratedTokensTotal = Counter
    .builder()
    .name("nixiesearch_inference_completion_generated_tokens_total")
    .help("Total number of generated tokens")
    .labelNames("model")
    .register(registry)

  val completionTimeSeconds = Gauge
    .builder()
    .name("nixiesearch_inference_completion_request_time_seconds")
    .help("Total embed query time in seconds")
    .labelNames("model")
    .register(registry)
}

package ai.nixiesearch.core.metrics

import io.prometheus.metrics.core.metrics.{Counter, Gauge}

case class InferenceMetrics() {
  val embedTotal = Counter
    .builder()
    .name("nixiesearch_inference_embedding_request_total")
    .help("Total number of embedding queries")
    .labelNames("model")
    .register()

  val embedDocTotal = Counter
    .builder()
    .name("nixiesearch_inference_embedding_doc_total")
    .help("Total number of embedded docs")
    .labelNames("model")
    .register()

  val embedTimeSeconds = Gauge
    .builder()
    .name("nixiesearch_inference_embedding_request_time_seconds")
    .help("Total embed query time in seconds")
    .labelNames("model")
    .register()

  val completionTotal = Counter
    .builder()
    .name("nixiesearch_inference_completion_request_total")
    .help("Total number of embedding queries")
    .labelNames("model")
    .register()

  val completionGeneratedTokensTotal = Counter
    .builder()
    .name("nixiesearch_inference_completion_generated_tokens_total")
    .help("Total number of generated tokens")
    .labelNames("model")
    .register()

  val completionTimeSeconds = Gauge
    .builder()
    .name("nixiesearch_inference_completion_request_time_seconds")
    .help("Total embed query time in seconds")
    .labelNames("model")
    .register()
}

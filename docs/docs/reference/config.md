# Config file

## Core config

Main server-related settings are stored here:

```yaml
core:
  host: 0.0.0.0 # optional, default=0.0.0.0
  port: 8080 # optional, default=8080
  loglevel: info # optional, default info
  telemetry: true # optional, default true
```

### Environment variables overrides

Core config settings can be overridden with env variables:

* `NIXIESEARCH_CORE_HOST`: overrides `core.host` 
* `NIXIESEARCH_CORE_PORT`: overrides `core.port`
* `NIXIESEARCH_CORE_LOGLEVEL`: overrides `core.loglevel`
* `NIXIESEARCH_CORE_TELEMETRY`: overrides `core.telemetry`

Loglevel can also be set from the [command-line flags](../reference/cli/standalone.md). Env overrides always have higher priority than config values.

### Telemetry configuration

You can opt-out of [anonymous usage telemetry collection](../help/usage_stats.md) by setting the `core.telemetry: false` option:

```yaml
core:
  telemetry: false
```

This is possible to also have a fine-grained control over telemetry parts from a config file (like error stack traces and performance metrics). But currently we only collect usage telemetry:

```yaml
core:
  telemetry:
    usage: false
```

## Index mapping

You can define each index in the `schema` block of the configuration:

```yaml
schema:
  <your-index-name>:
    alias: <list of aliases>
    config:
      <index configuration>
    store:
      <store configuration>
    fields:
      <field definitions>
```
!! note
    The index name is immutable, so choose it wisely. But you can always add an alias to address it using a new name.


### Index configuration

An example of index configuration:

```yaml
schema:
  index-name:
    config:
      indexer:
        ramBufferSize: 512mb
        flush:
          duration: 5s # how frequently new segments are created
      hnsw:
        m: 16 # max number of node-node links in HNSW graph
        efc: 100 # beam width used while building the index
        workers: 8 # how many concurrent workers used for HNSW merge ops
```

Fields:

* `indexer.flush.duration`: optional, duration, default `5s`. Index writer will periodically produce flush index segments (if there are new documents) with this interval.
* `indexer.ramBufferSize`: optional, size, default `512mb`. RAM buffer size for new segments.
* `hnsw.m`: optional, int, default 16. How many links should HNSW index have? Larger value means better recall, but higher memory usage and bigger index. Common values are within 16-128 range.
* `hnsw.efc`: optional, int, default 100. How many neighbors in the HNSW graph are explored during indexing. Bigger the value, better the recall, but slower the indexing speed.
* `hnsw.workers`: optional, int, default = number of CPU cores. How many concurrent workers to use for index merges.


### Store configuration

TODO

### Fields definitions

TODO

## ML Inference

See [ML Inference overview](../features/inference/overview.md) and [RAG Search](../features/search/rag.md) for an overview of use cases for inference models.

### Embedding models

#### ONNX models

Example of a full configuration:

```yaml
inference:
  embedding:
    your-model-name:
      provider: onnx
      model: nixiesearch/e5-small-v2-onnx
      file: model.onnx
      max_tokens: 512
      batch_size: 32
      pooling: mean
      normalize: true
      cache: false
      prompt:
        query: "query: "
        doc: "passage: "
```

Fields:

* `provider`: *optional*, *string*, default `onnx`. As for `v0.3.0`, only the `onnx` provider is supported.
* `model`: *required*, *string*. A [Huggingface](https://huggingface.co/models) handle, or an HTTP/Local/S3 URL for the model. See [model URL reference](url.md) for more details on how to load your model.
* `prompt`: *optional*. A document and query prefixes for asymmetrical models. Nixiesearch can guess the proper prompt format for the vast majority of embedding models. See the [list of supported embedding models](../features/inference/embeddings/sbert.md) for more details.
* `file`: *optional*, *string*, default is to pick a lexicographically first file. A file name of the model - useful when HF repo contains multiple versions of the same model.
* `max_tokens`: *optional*, *int*, default `512`. How many tokens from the input document to process. All tokens beyond the threshold are truncated.
* `batch_size`: *optional*, *int*, default `32`. Computing embeddings is a highly parallel task, and doing it in big chunks is much more effective than one by one. For CPUs there are usually no gains of batch sizes beyong 32, but on GPUs you can go up to 1024.
* `pooling`: *optional*, `cls`/`mean`, default auto. Which pooling method use to compute sentence embeddings. This is model specific and Nixiesearch tries to guess it automatically. See the [list of supported embeddings models](../features/inference/embeddings/sbert.md) to know if it can be detected automatically. If your model is not on the list, consult the model doc on its pooling method.
* `normalize`: *optional*, *bool*, default `true`. Should embeddings be L2-normalized? With normalized embeddings it becomes possible to use a faster dot-product based aKNN search.
* `cache`: *optional*, *bool* or [CacheSettings](#embedding-caching). Default `memory.max_size=32768`. Cache top-N LRU embeddings in RAM. See [Embedding caching](#embedding-caching) for more details.  

#### OpenAI models

Example of a full configuration:

```yaml
inference:
  embedding:
    <model-name>:
      provider: openai
      model: text-embedding-3-small
      timeout: 2000ms
      endpoint: "https://api.openai.com/"
      dimensions: null
      batch_size: 32
      cache: false

```

Parameters:

* **timeout**: optional, duration, default 2s. External APIs might be slow sometimes.
* **retry**: optional, string, default "https://api.openai.com/". You can use alternative API or EU-specific endpoint.
* **dimensions**: optional, int, default empty. For [matryoshka](https://huggingface.co/blog/matryoshka) models, how many dimensions to return.
* **batch_size**: optional, int, default 32. Batch size for calls with many documents.
* **cache**: optional, bool or [CacheSettings](#embedding-caching). Default `memory.max_size=32768`. Cache top-N LRU embeddings in RAM. See [Embedding caching](#embedding-caching) for more details.


#### Cohere models

Example of a full configuration:

```yaml
inference:
  embedding:
    <model-name>:
      provider: cohere
      model: embed-english-v3.0
      timeout: 2000ms
      endpoint: "https://api.cohere.com/"
      batch_size: 32
      cache: false
```

Parameters:

* **timeout**: optional, duration, default 2s. External APIs might be slow sometimes.
* **retry**: optional, string, default "https://api.cohere.com/". You can use alternative API or EU-specific endpoint.
* **batch_size**: optional, int, default 32. Batch size for calls with many documents.
* **cache**: optional, bool or [CacheSettings](#embedding-caching). Default `memory.max_size=32768`. Cache top-N LRU embeddings in RAM. See [Embedding caching](#embedding-caching) for more details.

#### Embedding caching

Each [embedding model](#embedding-models) has a `cache` section, which controls [embedding caching](../features/inference/embeddings/cache.md).

```yaml
inference:
  embedding:
    e5-small:
      model: nixiesearch/e5-small-v2-onnx
      cache:
        memory:
          max_size: 32768
```

Parameters:

* **cache**, optional, bool or object. Default `memory.max_size=32768`. Which cache implementation to use.
* **cache.memory.max_size**, optional, int, default 32768. How many string-embedding pairs to keep in the LRU cache.

Nixiesearch currently supports only `memory` embedding cache, [Redis caching](../features/inference/embeddings/cache.md#redis-cache) is planned.

### LLM completion models

Example of a full configuration:

```yaml
inference:
  completion:
    your-model-name:
      provider: llamacpp
      model: Qwen/Qwen2-0.5B-Instruct-GGUF
      file: qwen2-0_5b-instruct-q4_0.gguf
      system: "You are a helpful assistant, answer only in haiku."
      options:
        threads: 8
        gpu_layers: 100
        cont_batching: true
        flash_attn: true
        seed: 42
```

Fields:

* `provider`: *required*, *string*. As for `v0.3.0`, only `llamacpp` is supported. Other SaaS providers like OpenAI, Cohere, mxb and Google are on the roadmap.
* `model`: *required*, *string*. A [Huggingface](https://huggingface.co/models) handle, or an HTTP/Local/S3 URL for the model. See [model URL reference](url.md) for more details on how to load your model.
* `file`: *optional*, *string*. A file name for the model, if the target model has multiple. A typical case for quantized models.
* `system`: *optional*, *string*, default empty. An optional system prompt to be prepended to all the user prompts.
* `options`: *optional*, *obj*. A set of llama-cpp specific options. See [llamacpp reference on options](https://github.com/ggerganov/llama.cpp/blob/master/examples/main/README.md) for more details.


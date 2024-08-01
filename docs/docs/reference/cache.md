# Caching

## In-memory embedding cache

In practice many documents you ingest into index share a lot of common strings, like category names and colors. A common way to improve indexing throughput is to skip computing embeddings for common strings and instead just cache them.

Nixiesearch has an in-memory [LRU](https://en.wikipedia.org/wiki/Cache_replacement_policies) cache for common embeddings, which can be configured as follows:

```yaml
schema:
  my-index-name:
    fields:
      # fields here
    cache:
      embedding:
        maxSize: 32768
```

> The whole `cache` and `cache.embedding` sections of config file are optional.

Where:

* `cache.embedding.maxSize`: integer, optional, default=32768. Maximal number of entries in embedding [LRU](https://en.wikipedia.org/wiki/Cache_replacement_policies) cache.

A ballpark estimation of cache RAM usage:

* single embedding: `<dimensions> * 4 bytes`. Typical dimensions are `384` for MiniLM-L6/E5-small, and `768` for larger models.
* total usage: `<maxSize> * <embedding size>`

For example, a default `E5-small` embedding model with `32768` default cache size will take: `384 dims * 4 bytes * 32768 entries` = `50Mb` of heap RAM.
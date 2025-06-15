# In-Memory Persistence

In-memory persistence stores search indexes entirely in RAM using Lucene's `ByteBuffersDirectory`. This approach provides extremely fast search performance but comes with trade-offs around data persistence and memory usage.

For an overview of all storage options, see the [persistence overview](index.md). For production deployments, consider [S3 persistence](s3.md) or [local disk storage](local.md).

## When to Use In-Memory Storage

In-memory storage is perfect for development environments where you need fast iteration cycles and don't care about data persistence across restarts. It's ideal for performance testing when benchmarking search performance, as it eliminates disk I/O bottlenecks and provides pure computational performance metrics.

A common pattern is to use in-memory storage for searcher nodes in [distributed deployments](../overview.md), where searchers download indexes from [S3](s3.md) into memory for ultra-fast query processing, while indexers persist data to disk. It's also useful for short-lived search tasks or batch processing jobs where indexes don't need to survive beyond the process lifetime.

## Configuration Example

Basic in-memory storage configuration:

```yaml
core:
  cache:
    dir: /tmp/cache

inference:
  embedding:
    e5-small:
      model: intfloat/e5-small-v2

schema:
  movies:
    store:
      local:
        memory:
    fields:
      title:
        type: text
        search:
          lexical:
            analyze: en
          semantic:
            model: e5-small
      overview:
        type: text
        search:
          lexical:
            analyze: en
```

For more field configuration options, see the [schema mapping guide](../../../features/indexing/mapping.md). For embedding model configuration, see the [embeddings guide](../../../features/inference/embeddings.md).

## Kubernetes Deployment

When using in-memory storage in Kubernetes, ensure adequate memory allocation:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nixiesearch
spec:
  template:
    spec:
      containers:
      - name: nixiesearch
        image: nixiesearch/nixiesearch:latest
        resources:
          requests:
            memory: "2Gi"
          limits:
            memory: "4Gi"
```

For complete Kubernetes setup instructions, see the [Kubernetes deployment guide](../overview.md).

## Further Reading

- [Persistence overview](index.md) - Compare all storage options
- [S3 persistence](s3.md) - Distributed storage for production
- [Local disk storage](local.md) - Persistent local storage
- [Distributed deployment overview](../overview.md) - Production deployment patterns
- [Quickstart guide](../../../quickstart.md) - Complete development setup
# Index Persistence

Nixiesearch supports multiple persistence modes for storing search indexes, each optimized for different deployment scenarios and performance requirements. You can configure persistence per index in your schema configuration.

## Supported Persistence Modes

### In-Memory Storage
Stores search indexes entirely in RAM using Lucene's `ByteBuffersDirectory`. Provides ultra-fast search performance but all data is lost when the process stops.

**Configuration**: `store.local.memory`

See [in-memory persistence](inmem.md) for detailed configuration and usage.

### Local Directory Storage
Stores search indexes on the local filesystem using Lucene's standard directory implementation. Provides reliable data persistence with good performance for single-node deployments.

**Configuration**: `store.local.disk.path`

See [local directory persistence](local.md) for detailed configuration and usage.

### S3 Storage
Stores search index segments in S3-compatible object storage, enabling distributed deployments where multiple components can share index data. Supports any S3-compatible service including AWS S3, MinIO, Google Cloud Storage, and others.

**Configuration**: `store.distributed.remote.s3`

See [S3 persistence](s3.md) for detailed configuration and usage.

## When to Use Each Mode

### Use In-Memory Storage When:
- **Development and testing**: Fast iteration cycles without persistence requirements
- **Performance testing**: Eliminating I/O bottlenecks for pure performance metrics
- **Distributed searchers**: Fast query processing in distributed deployments where data is loaded from S3
- **Temporary workloads**: Short-lived tasks where indexes don't need to survive process restarts

### Use Local Directory Storage When:
- **Standalone deployments**: Single-node deployments handling both indexing and searching
- **Development with persistence**: Local development requiring data to survive restarts
- **Small to medium workloads**: Workloads that fit on a single machine's storage
- **Simple deployments**: When you want reliable persistence without distributed complexity

### Use S3 Storage When:
- **Production distributed deployments**: Scaling searcher and indexer components independently
- **Auto-scaling scenarios**: Stateless compute nodes that can be added/removed without data loss
- **Multi-environment sharing**: Sharing indexes between development, staging, and production
- **Multi-region deployments**: Consistent access to indexes across geographic locations
- **High availability**: Decoupling storage from compute for better reliability

## Hybrid Configurations

Nixiesearch supports mixing storage modes within the same deployment. A common pattern for distributed deployments is:

- **Searchers**: In-memory storage for fast query processing
- **Indexers**: In-memory or local disk for index building
- **Remote**: S3 storage for persistence and sharing between components

This approach combines the performance benefits of in-memory processing with the durability and scalability of S3 storage.

## Next Steps

- [In-memory persistence](inmem.md) - Fast ephemeral storage
- [Local directory persistence](local.md) - Persistent local storage  
- [S3 persistence](s3.md) - Distributed storage for production
- [Distributed deployment overview](../overview.md) - Complete deployment guide
- [Configuration reference](../../../reference/config.md) - All configuration options
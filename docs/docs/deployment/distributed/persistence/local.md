# Local Directory Persistence

Local directory persistence stores search indexes on the local filesystem using Lucene's standard directory implementation. This approach provides reliable data persistence with good performance for standalone deployments and development environments.

For an overview of all storage options, see the [persistence overview](index.md). For production distributed deployments, consider [S3 persistence](s3.md), and for development/testing, see [in-memory storage](inmem.md).

## When to Use Local Storage

Local storage is ideal for [standalone deployments](../../standalone.md) where you have a single Nixiesearch instance handling both indexing and searching on the same machine. It's perfect for development environments when you need persistent data across restarts but don't want the complexity of distributed storage.

Use local storage for small to medium workloads where your entire search index fits comfortably on a single machine's storage, and you don't need the scalability of distributed storage.

## Configuration Example

Basic local storage configuration:

```yaml
core:
  cache:
    dir: /data/cache

schema:
  movies:
    store:
      local:
        disk:
          path: /data/indexes
    fields:
      title:
        type: text
        search:
          lexical:
            analyze: en
      overview:
        type: text
        search:
          lexical:
            analyze: en
```

For more field configuration options, see the [schema mapping guide](../../../features/indexing/mapping.md). For configuration details, see the [configuration reference](../../../reference/config.md).

## Kubernetes Deployment

When using local storage in Kubernetes, configure persistent volumes:

```yaml
# PersistentVolumeClaim
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: nixiesearch-data
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi

# Deployment with volume mount
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nixiesearch
spec:
  template:
    spec:
      containers:
      - name: nixiesearch
        volumeMounts:
        - name: data-volume
          mountPath: /data
      volumes:
      - name: data-volume
        persistentVolumeClaim:
          claimName: nixiesearch-data
```

For complete Kubernetes setup instructions, see the [Kubernetes deployment guide](../overview.md).

## Further Reading

- [Persistence overview](index.md) - Compare all storage options
- [S3 persistence](s3.md) - Distributed storage for production
- [In-memory storage](inmem.md) - Fast ephemeral storage
- [Backup tutorial](../../../tutorial/backup.md) - Data protection strategies
- [Standalone deployment](../../standalone.md) - Complete standalone setup guide
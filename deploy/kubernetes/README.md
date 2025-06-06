# Nixiesearch Kubernetes Deployments

This directory contains Kubernetes manifests for deploying Nixiesearch in two different modes:

## Standalone

**Path**: `standalone/`

Single-pod deployment with persistent storage. Perfect for development, testing, and smaller workloads.

- All-in-one pod (search + indexing)
- Uses PersistentVolume for data storage
- Simple to deploy and manage
- 4 manifests: ConfigMap, PVC, Deployment, Service

```bash
cd standalone && kubectl apply -f .
```

## Distributed

**Path**: `distributed/`

Production-scale deployment with separate searcher and indexer components synchronized via S3.

- Multiple searcher pods (2 replicas)
- Single indexer pod (StatefulSet)
- S3-based index synchronization
- No persistent volumes needed
- 5 manifests: ConfigMap, Searcher Deployment/Service, Indexer StatefulSet/Service

```bash
cd distributed && kubectl apply -f .
```

## Documentation

See the [deployment overview](https://www.nixiesearch.ai/deployment/distributed/overview/) for detailed setup instructions and architecture diagrams.
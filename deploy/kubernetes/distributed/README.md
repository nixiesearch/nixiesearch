# Distributed Nixiesearch Kubernetes Deployment

This directory contains Kubernetes manifests for deploying Nixiesearch in distributed mode with S3-based index synchronization.

## Architecture

The distributed deployment separates concerns:
- **Searcher**: Handles search queries (2 replicas for HA)
- **Indexer**: Processes documents and builds indexes (1 replica)
- **S3**: Stores and synchronizes indexes between components

## Prerequisites

1. **S3 Bucket**: Create an S3 bucket for index storage
2. **AWS Credentials**: Set up AWS access (see configuration below)

## Quick Start

### 1. Configure S3 Settings

Edit `configmap.yaml` and update the S3 configuration:
```yaml
s3:
  bucket: your-bucket-name
  prefix: movies
  region: your-region
  # endpoint: http://minio:9000  # For MinIO/custom S3
```

### 2. Create AWS Credentials Secret (Optional)

```bash
kubectl create secret generic s3-credentials \
  --from-literal=access-key-id=YOUR_ACCESS_KEY \
  --from-literal=secret-access-key=YOUR_SECRET_KEY
```

Alternatively, use IAM roles or other AWS authentication methods.

### 3. Deploy All Components

```bash
# Apply all manifests
kubectl apply -f .

# Or apply individually
kubectl apply -f configmap.yaml
kubectl apply -f searcher-deployment.yaml
kubectl apply -f searcher-service.yaml
kubectl apply -f indexer-statefulset.yaml
kubectl apply -f indexer-service.yaml
```

## What's Included

- **`configmap.yaml`** - Shared configuration with S3 settings and schema
- **`searcher-deployment.yaml`** - Search service deployment (2 replicas)
- **`searcher-service.yaml`** - Service for search queries
- **`indexer-statefulset.yaml`** - Indexing service (1 replica)
- **`indexer-service.yaml`** - Service for indexing operations

## Accessing Services

### Search Service
```bash
# Within cluster
curl http://nixiesearch-searcher:8080/health

# Port forward for external access
kubectl port-forward svc/nixiesearch-searcher 8080:8080
curl http://localhost:8080/health
```

### Indexer Service
```bash
# Within cluster
curl http://nixiesearch-indexer:8080/health

# Port forward for external access
kubectl port-forward svc/nixiesearch-indexer 8081:8080
curl http://localhost:8081/health
```

## How It Works

1. **Indexer** builds search indexes in memory and uploads to S3
2. **Searcher** downloads indexes from S3 into memory for fast queries
3. **S3** acts as the persistent storage and synchronization mechanism

No persistent volumes are needed - all data persistence is handled via S3.

## Configuration Customization

### S3 Settings
Update the S3 configuration in `configmap.yaml`:
- `bucket`: Your S3 bucket name
- `prefix`: Path prefix for index storage
- `region`: AWS region
- `endpoint`: Custom S3 endpoint (for MinIO, etc.)

### Scaling
- **Searcher**: Increase replicas in `searcher-deployment.yaml` for higher query throughput
- **Indexer**: Keep at 1 replica to avoid conflicts

### Resource Tuning
Adjust resource requests/limits based on your workload:
- **Searcher**: Optimized for low latency, moderate memory
- **Indexer**: Requires more CPU/memory for index building

## Monitoring

Check component status:
```bash
# All pods
kubectl get pods -l app=nixiesearch

# Searcher pods
kubectl get pods -l component=searcher

# Indexer pods
kubectl get pods -l component=indexer

# Logs
kubectl logs -l component=searcher
kubectl logs -l component=indexer
```

## Cleanup

Remove all components:
```bash
kubectl delete -f .
```

This will not affect data stored in S3.
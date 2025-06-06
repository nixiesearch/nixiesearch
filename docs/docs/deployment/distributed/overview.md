# Running Nixiesearch in Kubernetes

Nixiesearch offers two main deployment patterns for [Kubernetes](https://kubernetes.io/), each designed for different use cases and operational requirements. This guide covers both approaches and helps you choose the right one for your needs.

For a broader overview of deployment options, see the [deployment overview](../overview.md). If you're new to Nixiesearch, consider starting with our [quickstart guide](../../quickstart.md).

## Deployment Options Overview

### Standalone Deployment

**Standalone Deployment** is perfect for getting started or running smaller workloads. Everything runs in a single pod with persistent storage for your data. For more details, see the [standalone deployment guide](../standalone.md).

```
┌─────────────────────────────────────┐
│           Kubernetes Pod            │
│ ┌─────────────────────────────────┐ │
│ │         Nixiesearch             │ │
│ │       (All services)            │ │
│ │  ┌─────────┐  ┌─────────────┐   │ │
│ │  │ Search  │  │   Indexing  │   │ │
│ │  │ Engine  │  │   Service   │   │ │
│ │  └─────────┘  └─────────────┘   │ │
│ └─────────────────────────────────┘ │
└─────────────────┬───────────────────┘
                  │
                  ▼
    ┌─────────────────────────────────┐
    │       Persistent Volume         │
    │  ┌─────────────┐ ┌───────────┐  │
    │  │   Indexes   │ │   Cache   │  │
    │  └─────────────┘ └───────────┘  │
    └─────────────────────────────────┘
```

### Distributed Deployment

**Distributed Deployment** is built for production scale. It separates search and indexing into different components that communicate through [S3](https://aws.amazon.com/s3/), giving you better performance and reliability.

```
┌─────────────────┐    ┌─────────────────┐
│   Searcher      │    │   Searcher      │
│   pod #1        │    │   pod #2        │
│ ┌─────────────┐ │    │ ┌─────────────┐ │
│ │Search engine│ │    │ │Search engine│ │
│ │(inmem index)│ │    │ │(inmem index)│ │
│ └─────────────┘ │    │ └─────────────┘ │
└─────────┬───────┘    └─────────┬───────┘
          │                      │
          └──────────┬───────────┘
                     │ Download index segments
                     ▼
              ┌─────────────┐
              │     S3      │
              │   storage   │
              │  (indexes)  │
              └─────────────┘
                     ▲
                     │ Upload index segments
                     │
┌─────────────────────────────────────┐
│           Indexer pod               │
│ ┌─────────────────────────────────┐ │
│ │        Nixiesearch indexer      │ │
│ │  ┌─────────────┐ ┌───────────┐  │ │
│ │  │   Document  │ │   Index   │  │ │
│ │  │ processing  │ │ building  │  │ │
│ │  └─────────────┘ └───────────┘  │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

## Standalone Deployment

The standalone deployment is the simplest way to run Nixiesearch on Kubernetes. It's great for development, testing, or smaller production workloads where you want to keep things simple.

### What You Get

With standalone deployment, you get a single pod running all Nixiesearch functionality. Your data is stored on a persistent volume to keep it safe across pod restarts. The configuration is minimal with very few moving parts, and you can easily scale by adjusting pod resources.

### Getting Started

The standalone deployment comes with everything you need in the `deploy/kubernetes/standalone` directory. You'll find the configuration setup with a quickstart movie schema, a 10GB persistent volume for your data, the main Nixiesearch application manifest, and a service that exposes the app on port 8080. The configuration uses the same [schema mapping](../../features/indexing/mapping.md) principles as other deployments.

```bash
# Deploy everything at once
cd deploy/kubernetes/standalone
kubectl apply -f .
```

Your data gets organized under `/data` inside the pod, with search indexes stored in `/data/indexes` and cache files in `/data/cache`.

### Manifests Overview

The standalone deployment includes these YAML manifests:

- **`configmap.yaml`** - Contains Nixiesearch configuration with quickstart movie schema, persistent storage paths, and embedding model settings
- **`pvc.yaml`** - PersistentVolumeClaim requesting 10GB storage for indexes and cache data
- **`deployment.yaml`** - Main Nixiesearch application running in standalone mode with volume mounts and health checks
- **`service.yaml`** - ClusterIP service exposing the application on port 8080 for internal cluster access

### When to Use Standalone

Standalone deployment works well for development and testing since it's quick to set up and tear down. It handles small to medium workloads effectively when you don't need separate scaling of components. The operational simplicity means fewer components to manage, making it easier to understand and debug while you're learning Nixiesearch.

### Storage Considerations

The standalone deployment relies on a [PersistentVolumeClaim](https://kubernetes.io/docs/concepts/storage/persistent-volumes/), so your cluster should have either a default storage class configured or you'll need to specify one in the PVC manifest. The beauty of this approach is that your data persists across pod restarts, which is exactly what you want for a search index that takes time to build. For configuration details, see the [cache configuration](../../reference/cache.md) documentation.

## Distributed Deployment

The distributed deployment splits Nixiesearch into specialized components. This approach gives you better performance, scalability, and operational flexibility for production workloads where you need more control over how different parts of the system behave.

### What You Get

In a distributed setup, you get dedicated searcher pods (typically 2 replicas) that handle all search queries, plus a single indexer pod that processes documents and builds indexes. Everything stays synchronized through [S3](https://aws.amazon.com/s3/) storage, and you can scale each component independently. The multiple searcher replicas provide high availability for your search service.

### Components Deep Dive

The **searcher** component focuses entirely on handling search queries with low latency. It runs with 2 replicas by default, though you can scale this up for higher throughput. These pods download indexes from S3 into memory for the fastest possible access, and their resources are optimized for query performance rather than index building.

The **indexer** component handles document processing and index building. It always runs as a single replica to avoid indexing conflicts that could corrupt your data. This component builds indexes in memory and uploads completed indexes to S3. Since index building is resource-intensive, the indexer is allocated more CPU and memory than the searchers.

**S3 integration** provides the backbone for the entire system. All search indexes live in [S3](https://aws.amazon.com/s3/), with the indexer uploading new or updated indexes and searchers downloading them as needed. This eliminates the need for any local persistent volumes and works with [AWS S3](https://aws.amazon.com/s3/), [MinIO](https://min.io/), or any S3-compatible storage service.

### Getting Started

The distributed deployment lives in `deploy/kubernetes/distributed` and includes shared configuration with S3 settings, separate manifests for the searcher [Deployment](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/) and [Service](https://kubernetes.io/docs/concepts/services-networking/service/), and dedicated manifests for the indexer [StatefulSet](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/) and Service.

Before deploying, you'll need an [S3 bucket](https://docs.aws.amazon.com/s3/latest/userguide/creating-buckets-s3.html) for storing indexes and [AWS credentials](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-authentication.html) configured (though [IAM roles](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles.html) are recommended over explicit credentials).

To configure the system, edit the S3 settings in `configmap.yaml` to specify your bucket name, path prefix, and AWS region. If you're using [MinIO](https://min.io/) or another S3-compatible service, uncomment and set the endpoint URL. For detailed configuration options, see the [configuration reference](../../reference/config.md).

```yaml
s3:
  bucket: your-nixiesearch-indexes
  prefix: movies
  region: us-west-2
  # endpoint: http://minio:9000  # For MinIO
```

For deployment, you can optionally set up explicit credentials using [Kubernetes Secrets](https://kubernetes.io/docs/concepts/configuration/secret/), though [IAM roles](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles.html) are preferred:

```bash
# Set up credentials (optional if using IAM roles)
kubectl create secret generic s3-credentials \
  --from-literal=access-key-id=YOUR_KEY \
  --from-literal=secret-access-key=YOUR_SECRET

# Deploy all components
cd deploy/kubernetes/distributed  
kubectl apply -f .
```

### Manifests Overview

The distributed deployment includes these YAML manifests:

- **`configmap.yaml`** - Shared configuration with S3 settings, embedding models, and distributed store configuration for both searcher and indexer components
- **`searcher-deployment.yaml`** - Deployment running 2 searcher replicas optimized for query performance with memory-based indexes
- **`searcher-service.yaml`** - ClusterIP service providing stable network endpoint for search queries
- **`indexer-statefulset.yaml`** - StatefulSet with single indexer pod for document processing and index building with higher resource allocation
- **`indexer-service.yaml`** - ClusterIP service for indexing operations and administrative tasks

### When to Use Distributed

Distributed deployment shines for production workloads where you need better reliability and performance. It handles high query volumes well since you can scale searchers independently of the indexer. Large datasets benefit from having dedicated indexer resources that don't compete with search performance. The architecture also supports multi-environment setups where you can share indexes between development, staging, and production via [S3](https://aws.amazon.com/s3/). Perhaps most importantly, it gives you operational flexibility to update the indexer without affecting search traffic, or vice versa. For operational best practices, see the [autoscaling guide](../../tutorial/autoscaling.md).

### Operational Benefits

The independent scaling capability means you can add more searcher replicas when you need higher query throughput, or allocate more resources to the indexer during heavy indexing periods, all without affecting the other component. For scaling strategies, see the [autoscaling tutorial](../../tutorial/autoscaling.md).

Zero-downtime updates become possible since you can update the indexer without impacting search queries, or roll out searcher updates gradually across replicas. See the [upgrade guide](../../tutorial/upgrade.md) for best practices.

Disaster recovery is simplified because indexes are safely stored in [S3](https://aws.amazon.com/s3/). If you lose a pod, it automatically rebuilds from S3 without any manual intervention. For backup strategies, see the [backup tutorial](../../tutorial/backup.md).

Cost optimization opportunities emerge since you can run indexing workloads on larger instances (potentially even [spot instances](https://aws.amazon.com/ec2/spot/)) while keeping searchers on reliable, right-sized compute resources.

## Choosing the Right Deployment

**Standalone deployment** makes sense when you're just getting started with Nixiesearch, working with relatively small datasets (under 1 million documents), prefer operational simplicity, or don't need independent scaling of search versus indexing components. For a detailed guide, see the [standalone deployment documentation](../standalone.md).

**Distributed deployment** becomes the better choice for production workloads, high query volumes or large datasets, situations where you want the flexibility to scale components independently, environments where you're already using [S3](https://aws.amazon.com/s3/) for other storage needs, or when you need high availability for search queries.

Both deployments are production-ready, so the choice really comes down to your specific requirements and operational preferences. Many teams start with standalone deployment to get familiar with Nixiesearch, then migrate to distributed as their needs grow and they want more operational control.

## Next Steps

Once you have your deployment running, you might want to:

- Set up monitoring with [Prometheus metrics](prometheus.md)
- Configure [document indexing](indexing/overview.md) for your data sources  
- Learn about [search features](../../features/search/overview.md) and [API usage](../../api.md)
- Explore [autoscaling strategies](../../tutorial/autoscaling.md) for production workloads
- Set up [backup and recovery](../../tutorial/backup.md) procedures

For troubleshooting and operational questions, see our [support resources](../../help/support.md).
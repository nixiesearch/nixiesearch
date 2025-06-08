# Backup and Restore

## Overview

Nixiesearch's S3-native architecture provides significant advantages for backup and restore operations. Unlike traditional search engines that require complex volume snapshots, nixiesearch stores all index data directly in S3, making backups as simple as copying S3 objects.

This tutorial covers:

- **Backup scenarios**: Regular backups vs disaster recovery
- **Restore procedures**: Point-in-time and cross-environment restores
- **Key concepts**: S3 prefix changes and ConfigMap rollouts for zero-downtime operations

## Prerequisites

Before starting, ensure you have:

- A distributed nixiesearch cluster running in Kubernetes (see [Distributed Deployment](../deployment/distributed/kubernetes.md))
- S3 bucket with appropriate read/write permissions (see [S3 Persistence](../deployment/distributed/persistence/s3.md))
- AWS CLI installed and configured
- `kubectl` access to your Kubernetes cluster

## Backup

### Manual Backup

Create a point-in-time backup by copying your current S3 prefix:

```bash
# Backup current index data
aws s3 sync s3://nixiesearch-indexes/movies s3://nixiesearch-indexes/backup-movies-2024-01-15

# Backup Kubernetes configuration
kubectl get configmap nixiesearch-config -o yaml > nixiesearch-config-backup.yaml
kubectl get deployment nixiesearch-searcher -o yaml > searcher-deployment-backup.yaml
kubectl get statefulset nixiesearch-indexer -o yaml > indexer-statefulset-backup.yaml
```

!!! note

    Underlying Lucene index consists of multiple immutable segments. Segments cannot be altered, but can be removed during the compaction process. Nixiesearch indexer only deletes actual index segments after a fixed 1h time interval to make S3 index operations atomic.

### Automated Backup

Set up automated backups using S3 lifecycle policies:

```json
{
  "Rules": [
    {
      "ID": "NixiesearchBackup",
      "Status": "Enabled",
      "Filter": {"Prefix": "movies"},
      "Transitions": [
        {
          "Days": 30,
          "StorageClass": "STANDARD_IA"
        },
        {
          "Days": 90,
          "StorageClass": "GLACIER"
        }
      ]
    }
  ]
}
```

For cross-region disaster recovery, enable S3 cross-region replication:

```bash
aws s3api put-bucket-replication \
  --bucket nixiesearch-indexes \
  --replication-configuration file://replication-config.json
```

## Restore

### Point-in-Time Restore

Restore from a specific backup by updating the S3 prefix in your [configuration](../reference/config.md):

1. **Copy backup data to new prefix**:
```bash
aws s3 sync s3://nixiesearch-indexes/backup-movies-2024-01-15 s3://nixiesearch-indexes/movies-restored
```

2. **Update ConfigMap with new prefix**:
```bash
kubectl patch configmap nixiesearch-config --patch '
data:
  config.yml: |
    schema:
      movies:
        store:
          distributed:
            searcher:
              memory:
            indexer:
              memory:
            remote:
              s3:
                bucket: nixiesearch-indexes
                prefix: movies-restored
                region: us-east-1
        fields:
          # ... rest of your schema configuration
'
```

3. **Rolling restart to pick up new configuration**:
```bash
kubectl rollout restart deployment/nixiesearch-searcher
kubectl rollout restart statefulset/nixiesearch-indexer
```

4. **Verify restore**:
```bash
# Check pod status
kubectl get pods -l app=nixiesearch

# Test search functionality
kubectl port-forward svc/nixiesearch-searcher 8080:8080
curl "http://localhost:8080/movies/_search?q=test"
```

## Zero-Downtime Restore

For production environments, use a blue-green deployment approach:

1. **Create new ConfigMap with restored prefix**:
```bash
kubectl create configmap nixiesearch-config-restored --from-file=config.yml=restored-config.yml
```

2. **Deploy new searcher deployment**:
```bash
# Update searcher deployment to use new ConfigMap
kubectl patch deployment nixiesearch-searcher --patch '
spec:
  template:
    spec:
      volumes:
      - name: config
        configMap:
          name: nixiesearch-config-restored
'
```

3. **Wait for new pods to be ready**:
```bash
kubectl rollout status deployment/nixiesearch-searcher
```

4. **Switch traffic** by updating the service selector if needed, or simply let Kubernetes handle the rolling update.

5. **Update indexer** once searchers are healthy:
```bash
kubectl patch statefulset nixiesearch-indexer --patch '
spec:
  template:
    spec:
      volumes:
      - name: config
        configMap:
          name: nixiesearch-config-restored
'
```

## Further Reading

- [Distributed Deployment Overview](../deployment/distributed/overview.md) - Architecture and concepts
- [S3 Persistence Configuration](../deployment/distributed/persistence/s3.md) - Detailed S3 setup
- [Schema Migration](schema.md) - Handling schema changes during restore
- [Version Upgrades](upgrade.md) - Combining backups with version updates
- [Configuration Reference](../reference/config.md) - Complete configuration options
- [Auto-scaling](autoscaling.md) - Scaling considerations for restored clusters

!!! note 
    
    Remember that nixiesearch's stateless architecture means no persistent volumes need backing up - all critical data lives in S3 and can be restored by simply updating configuration.
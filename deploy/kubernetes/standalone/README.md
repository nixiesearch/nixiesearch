# Standalone k8s deployment

This directory contains Kubernetes manifests for deploying Nixiesearch in standalone mode with PV/PVC persistent storage. Standalone deployment

## Quick Start

Deploy Nixiesearch with persistent storage:

```bash
# Apply all manifests
kubectl apply -f .

# Or apply individually in the recommended order
kubectl apply -f configmap.yaml
kubectl apply -f pvc.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
```

## What's Included

- **`configmap.yaml`** - Configuration with quickstart schema and persistent storage paths
- **`pvc.yaml`** - PersistentVolumeClaim for data storage (10Gi by default)
- **`deployment.yaml`** - Nixiesearch deployment with mounted persistent volume
- **`service.yaml`** - ClusterIP service exposing port 8080

## Storage Configuration

The deployment uses persistent storage mounted at `/data`:
- Index data: `/data/indexes`
- Cache data: `/data/cache`

This ensures your search indexes and cache persist across pod restarts.

### Configuring Storage Class

The PVC uses your cluster's default storage class. To use a specific storage class, uncomment and modify the `storageClassName` in `pvc.yaml`:

```yaml
# Common examples:
storageClassName: gp2          # AWS EBS
storageClassName: pd-standard  # Google Cloud Persistent Disk
storageClassName: default      # Cluster default
```

## Accessing Nixiesearch

### From within the cluster:
```bash
curl http://nixiesearch:8080/health
```

### For local development (port forwarding):
```bash
kubectl port-forward svc/nixiesearch 8080:8080
curl http://localhost:8080/health
```

### For external access (if your cluster supports it):
Change the service type in `service.yaml`:
```yaml
spec:
  type: LoadBalancer  # or NodePort
```

## Customizing Configuration

You can modify the Nixiesearch configuration by editing `configmap.yaml`. After making changes:

```bash
kubectl apply -f configmap.yaml
kubectl rollout restart deployment/nixiesearch
```

This allows you to adjust index schemas, embedding models, storage paths, and other settings.

## Monitoring and Troubleshooting

Check deployment status:
```bash
kubectl get pods -l app=nixiesearch
kubectl logs -l app=nixiesearch
kubectl describe deployment nixiesearch
```

## Cleanup

To remove all Nixiesearch resources:
```bash
kubectl delete -f .
```

**Important:** This will delete the PVC and permanently remove all indexed data. Make sure to backup any important data before cleanup.
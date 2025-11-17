# Deployment

Nixiesearch is distributed as a [single Docker container](../quickstart.md#prerequisites) (with a special flavors for CPU and [GPU inference](distributed/gpu.md)) and can be deployed as a regular dockerized app:

* **[Standalone](#standalone)**: simple single-node install with [Docker](https://hub.docker.com/r/nixiesearch/nixiesearch/tags), with search and index API colocated within a single process. Recommended for dev environments and installs with no need for fault tolerance.
* **[Distributed](#distributed)**: distributed setup with separate indexer and searcher services, backed by S3-compatible block storage. Recommended for large and fault-tolerant installs.

## Docker Images

Nixiesearch offers different Docker image variants to match your deployment requirements:

### JDK-Based Images

Standard images built with OpenJDK, tagged as `<version>` (e.g., `0.8.0`, `latest`):

- **Architecture support**: x86_64 and arm64
- **Features**: Full ONNX embedding inference and llamacpp LLM support
- **Use cases**: Production deployments with semantic search and RAG capabilities
- **GPU variant**: Available with `-gpu` suffix (e.g., `latest-gpu`) for CUDA acceleration

### Native Images

GraalVM Native Image builds for faster startup, tagged as `<version>-native` (e.g., `0.8.0-native`, `latest-native`):

- **Architecture support**: x86_64 only
- **Features**: Lexical search and API-based inference only (no ONNX or llamacpp)
- **Use cases**: Serverless deployments (Lambda), environments requiring fast cold starts
- **Status**: Highly experimental

Choose JDK-based images for production workloads with full feature support, or native images for serverless deployments where fast startup matters more than local inference capabilities.

## Standalone

In standalone mode searcher and indexer are colocated on a single Nixiesearch process:

![standalone](../img/standalone.png)

Standalone mode is easy to start as it enforces a set of simplifications for the setup:

* only [REST API based document indexing](../api.md) is possible. 
* [S3-based index sync](distributed/persistence/index.md) is not enabled.

To start nixiesearch in a standalone mode, use the [nixiesearch standalone](../reference/cli/standalone.md) subcommand (where `config.yml` is an [index mapping configuration file](../features/indexing/mapping.md)):

```shell
docker run -i -t -v <data dir>:/data/ -p 8080:8080 nixiesearch/nixiesearch:latest\
  standalone --config config.yml
```

## Distributed

For production installs Nixiesearch can be rolled out in a distributed manner:

* separate searchers and indexer. Potentially searchers can be auto-scaled, and even scaled to zero.
* sync between indexer and searchers happens via S3-compatible block storage.

![distributed](../img/distrubuted.png)

Nixiesearch has separate [nixiesearch index](../reference/cli/index.md) and [nixiesearch search](../reference/cli/search.md) sub-commands to run these sub-tasks.

Helm chart for smooth k8s deployment is planned for v0.3.


# Standalone Docker Deployment

Nixiesearch can be run as a single Docker container for local development, testing, and small-scale production workloads. This deployment method combines indexing and search functionality in a single process, making it the simplest way to get started with Nixiesearch. For a complete introduction, see the [quickstart guide](../quickstart.md).

## Configuration Setup

Create a minimal `config.yml` file for standalone operation. For complete configuration options, see the [configuration reference](../reference/config.md):

```yaml
store:
  local:
    path: /data

schema:
  movies:
    fields:
      title:
        type: text
        search:
          lexical:
            analyze: english
      overview:
        type: text
        search:
          lexical:
            analyze: english
      year:
        type: int
        facet: true
```

For more details on field types and mapping configuration, see the [index mapping documentation](../features/indexing/mapping.md).

### With Semantic Search and LLM Features

To enable [semantic search](../features/search/query/retrieve/semantic.md), [embeddings](../features/inference/embeddings.md), and [LLM capabilities](../features/inference/completions.md):

```yaml
store:
  local:
    path: /data

inference:
  embedding:
    e5-small:
      model: intfloat/e5-small-v2
  completion:
    qwen2:
      provider: llamacpp
      model: Qwen/Qwen2-0.5B-Instruct-GGUF
      file: qwen2-0_5b-instruct-q4_0.gguf

schema:
  movies:
    fields:
      title:
        type: text
        search:
          lexical: 
            analyze: english
          semantic:
            model: e5-small
      overview:
        type: text
        search:
          lexical:
            analyze: english
          semantic:
            model: e5-small
      year:
        type: int
        facet: true
```

## Local Storage Setup

Nixiesearch standalone mode uses local filesystem storage for index persistence. 

```bash
docker run -p 8080:8080 \
  -v $(pwd)/config.yml:/config.yml \
  -v $(pwd)/data:/data \
  nixiesearch/nixiesearch:latest \
  standalone --config /config.yml
```

This exposes the API on port 8080, mounts your configuration file, creates a local data directory for index storage, and runs in standalone mode.

!!! note

    **Storage Considerations**: Plan for 1.5-2x your document size for index storage. LLM models can be 100MB-2GB each, so ensure adequate space. Use SSD storage for better search performance.

## Docker Compose Setup

For easier management, use Docker Compose:

```yaml
version: '3.8'

services:
  nixiesearch:
    image: nixiesearch/nixiesearch:latest
    command: standalone --config /config.yml
    ports:
      - "8080:8080"
    volumes:
      - ./config.yml:/config.yml
      - ./data:/data
      - ./models:/models
    environment:
      - JAVA_OPTS=-Xmx2g
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

Start with `docker compose up`.

## Usage Examples

### Indexing Documents

```bash
curl -X POST http://localhost:8080/v1/index/movies \
  -H "Content-Type: application/json" \
  -d '{
    "title": "The Matrix",
    "overview": "A computer programmer discovers reality is a simulation",
    "year": 1999
  }'
```

### Basic Search

Use [match queries](../features/search/query/retrieve/match.md) for lexical search:

```bash
curl -X POST http://localhost:8080/v1/index/movies/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": {"match": {"title": "matrix"}},
    "fields": ["title"],
    "size": 10
  }'
```

### Semantic Search

Use [semantic queries](../features/search/query/retrieve/semantic.md) for vector-based search:

```bash
curl -X POST http://localhost:8080/v1/index/movies/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": {"semantic": {"overview": "computer simulation reality"}},
    "fields": ["title"],
    "size": 5
  }'
```

For more query examples, see the [search overview](../features/search/overview.md) and [query DSL documentation](../features/search/query/overview.md).


## Monitoring and Maintenance

Check service health with `curl http://localhost:8080/health` and monitor index statistics using `curl http://localhost:8080/v1/index/movies/_stats`. View container logs with `docker logs nixiesearch-container` or follow them in real-time with `docker logs -f nixiesearch-container`. For advanced monitoring setup, see the [metrics documentation](distributed/prometheus.md).

## Next Steps: Distributed Deployment

For production workloads requiring higher availability and scale, see the [distributed deployment guide](distributed/overview.md).
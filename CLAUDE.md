# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Nixiesearch is a modern S3-based search engine built with Scala 3 and Apache Lucene. It provides hybrid search (lexical + semantic), AI-powered features (embeddings, LLM inference), and operates with stateless compute nodes backed by S3 storage.

## Build System & Commands

**Build Tool**: SBT with Scala 3.7.1

### Core Commands
```bash
# Run all tests
sbt test

# Build fat JAR
sbt assembly

# Build Docker images (with multi-arch support)
sbt dockerBuildAndPush

# Run specific test
sbt "testOnly *TestClassName*"

# Run tests by category
sbt "testOnly -- -n HttpTest"
sbt "testOnly -- -n SlowTest"
```

### Docker Development
```bash
# Build and run standalone mode
docker run -itp 8080:8080 -v .:/data nixiesearch/nixiesearch:latest standalone -c /data/config.yml

# GPU variant available
docker run nixiesearch/nixiesearch:latest-gpu
```

### Test Categories
Tests are tagged with ScalaTest categories:
- `HttpTest` - API integration tests
- `SlowTest` - Long-running tests
- Use these for selective test execution

## Architecture

### Core Design Principles
1. **Stateless compute**: All state lives in S3, compute nodes are ephemeral
2. **Configuration-as-code**: Immutable YAML configs drive all behavior
3. **Hybrid search**: Combines BM25 lexical and vector semantic search
4. **AI-first**: Built-in embedding inference and LLM integration

### Package Structure
- **`ai.nixiesearch.api`**: HTTP4s REST API, query DSL, JSON codecs
- **`ai.nixiesearch.core`**: Document/field system, neural networks, metrics
- **`ai.nixiesearch.config`**: YAML configuration with environment substitution
- **`ai.nixiesearch.index`**: Indexing engine, storage abstraction, sync
- **`ai.nixiesearch.main`**: CLI modes (standalone, search, index)
- **`ai.nixiesearch.source`**: Data ingestion (file, Kafka streams)

### Key Patterns

**Resource Management**: All IO uses Cats Effect Resource for proper cleanup
```scala
def openIndex(path: Path): Resource[IO, IndexReader] = 
  Resource.make(acquire)(release)
```

**Configuration**: Environment variable substitution in YAML
```yaml
api:
  host: ${HOST:-localhost}  # Defaults to localhost
  port: ${PORT:-8080}
```

**Error Handling**: ADT-based errors with proper HTTP status mapping
```scala
sealed trait APIError
case class IndexNotFound(name: String) extends APIError
```

## Application Modes

**Standalone Mode**: Single process handles indexing and search
```bash
java -jar nixiesearch.jar standalone --config config.yml
```

**Distributed Mode**: Separate indexer and searcher processes
```bash
# Indexer process
java -jar nixiesearch.jar index --config config.yml

# Searcher process  
java -jar nixiesearch.jar search --config config.yml
```

## Configuration System

Configuration is YAML-based with these key sections:
- `inference`: Embedding models and LLM providers
- `schema`: Index mappings and field definitions
- `api`: HTTP server settings
- `core`: Cache and system settings

Environment variables override config values using `${VAR:-default}` syntax.

## Storage Architecture

**Local Development**: Uses local file system
**Production**: All indexes stored in S3 with local caching

Index sync pattern:
1. Indexer writes to S3
2. Searchers poll S3 for updates
3. Download and swap indexes atomically

## Testing Notes

- Use `LocalNixie` trait for integration tests
- Mock external services (S3, embedding APIs) in unit tests
- E2E tests run against real Docker containers
- Performance tests tagged as `SlowTest`

## Development Workflow

1. Tests are comprehensive - run relevant test categories during development
2. Configuration changes require restart (immutable config pattern)
3. Use `standalone` mode for local development and testing
4. Docker is the primary deployment target
5. All persistent state should go through the storage abstraction layer

## Common Gotchas

- Lucene indexes are write-once, read-many (affects concurrent access patterns)
- S3 eventual consistency can affect distributed deployments
- Embedding model loading is expensive - cache appropriately
- Configuration validation happens at startup - invalid configs fail fast
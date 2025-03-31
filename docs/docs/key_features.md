# Key Features

Nixiesearch is built to make search simple, scalable, and safe - especially compared to the complexity of running [Elasticsearch](https://elastic.co) or [OpenSearch](https://opensearch.org) in production. Here are a few of the standout features that make Nixiesearch different:

## 🔒 Schemaless is not OK

Unlike databases, where schemaless design often leads to [chaos](https://www.mongodb.com/), many search engines (we're watching at you,  [Elasticsearch](https://elastic.co)) still let you get away with it, until it breaks everything.

Why it matters:

* **Mapping changes can wreck your index**: In Elasticsearch, altering a mapping in an incompatible way while running indexing can silently break your queries, when half of documents are processed in a new way, and half of them stays in an old format.

* Nixiesearch **enforces [document schema](features/indexing/mapping.md) on ingestion**, and validates correctness of schema migrations (so you cannot flip field type from `int` to `text` without explicit reindexing). You always know exactly what’s indexed and how. No surprises, no “uh-oh” moments.

[Wildcard support](features/indexing/mapping.md#wildcard-fields) is still there when you really need flexibility, but only in a controlled and explicit way.

## 📦 Immutable S3-Based Indexes

Storing indexes on S3 isn't just a cost-saver - it changes the game operationally.

What’s different:

* **Truly immutable index files**: No risk of corruption or half-written segments. Snapshots and backups are just… copies on S3.
* **No cluster state**: All [config lives in plain text](reference/config.md). Upgrades, schema changes, and rollbacks are simple file updates. You can GitOps your whole search setup in a simple ArgoCD manifest.
* **Zero-downtime everything**: Want to change schema? Just deploy a new config and do a rolling or blue-green restart of the [Kubernetes Deployment](tutorial/schema.md). No need to babysit an existing cluster.

## 🧲 Pull-Based Indexing

Indexing should never interfere with your search workload.

What’s different:

* Nixiesearch uses a [pull-based architecture](features/indexing/overview.md#streaming-document-indexing): **indexing runs in a separate tier**, so your search nodes never get overloaded.
* Supports both **offline batch** (for reindexing) and **real-time streaming** ([Kafka](deployment/distributed/indexing/kafka.md), [S3](deployment/distributed/indexing/file.md), etc.), so you can scale indexing independently.
* You get **[natural backpressure](tutorial/indexing.md) and safe ingestion by design**, without needing to tune queues or buffers.

## 🧠 Power of Apache Lucene 

At its core, Nixiesearch runs on [Apache Lucene](https://lucene.apache.org) — the same battle-tested engine behind Elasticsearch, but without the operational baggage.

What you get:

* [Lexical](features/search/overview.md#search), semantic, and [hybrid search with Reciprocal Rank Fusion](features/search/overview.md#hybrid-search-with-reciprocal-rank-fusion) (RRF)
* [Facets](features/search/facet.md), [filters](features/search/filter.md), [autocomplete](features/autocomplete/index.md), and all the [advanced query](features/search/query.md) goodness.
* [Embedding inference](features/inference/embeddings.md) and [RAG](features/search/rag.md) out of the box—no extra services needed.

And yes, it's fully open-source—no feature gating or “you need Enterprise” surprises.

----

Whether you're migrating from Elasticsearch or starting fresh, Nixiesearch offers a radically simpler way to build production-grade search. It's the search engine that feels like infrastructure should in 2025: immutable, stateless, scalable, and smart.
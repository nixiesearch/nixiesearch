# Nixiesearch: batteries included search engine

![logo with name](img/logo-with-title.png)


[![CI Status](https://github.com/nixiesearch/nixiesearch/workflows/Tests/badge.svg)](https://github.com/nixiesearch/nixiesearch/actions)
[![License: Apache 2](https://img.shields.io/badge/License-Apache2-green.svg)](https://opensource.org/licenses/Apache-2.0)
![Last commit](https://img.shields.io/github/last-commit/nixiesearch/nixiesearch)
![Last release](https://img.shields.io/github/release/nixiesearch/nixiesearch)
[![Join our slack](https://img.shields.io/badge/Slack-join%20the%20community-blue?logo=slack&style=social)](https://communityinviter.com/apps/nixiesearch/nixiesearch)
[![Visit demo](https://img.shields.io/badge/visit-demo-blue)](https://demo.nixiesearch.ai)

## What is Nixiesearch?

Nixiesearch is a **modern search engine** that runs on [S3-compatible storage](deployment/distributed/persistence/s3.md). We built it after dealing with the headaches of running large Elastic/OpenSearch clusters (here's the [blog post full of pain](https://nixiesearch.substack.com/p/nixiesearch-running-lucene-over-s3)), and here’s why it’s awesome:

* **Powered by [Apache Lucene](https://lucene.apache.org)**: You get support for [39 languages](reference/languages.md), [facets](features/search/facet.md), [advanced filters](features/search/filter.md), [autocomplete suggestions](features/autocomplete/index.md), and the familiar [sorting](features/search/sort.md) features you’re used to.
* **Decoupled [S3-based](deployment/distributed/persistence/s3.md) storage and compute**: There's nothing to break. You get risk-free [backups](tutorial/backup.md), [upgrades](tutorial/upgrade.md), [schema changes](tutorial/schema.md) and [auto-scaling](tutorial/autoscaling.md), all on a stateless index stored in S3.
* **Pull indexing**: Supports both offline and online incremental indexing using an [Apache Spark based ETL process](features/indexing/overview.md). No more POSTing JSON blobs to prod cluster (and overloading it).
* **No state inside the cluster**: All changes (settings, indexes, etc.) are just [config](reference/config.md) updates, which makes [blue-green deployments](tutorial/schema.md) of index changes a breeze.
* **AI batteries included**: [Embedding](features/inference/embeddings.md) and [LLM inference](features/inference/completions.md), first class [RAG API](features/search/rag.md) support.

![NS design diagram](img/arch.png)

Search is never easy, but Nixiesearch has your back. It takes care of the toughest parts—like reindexing, capacity planning, and maintenance—so you can save time (and your sanity).

!!! note 
    Want to learn more? Go straight to the [quickstart](https://www.nixiesearch.ai/quickstart/) and check out [the live demo](https://demo.nixiesearch.ai).

## What Nixiesearch is not?

* **Nixiesearch is not a database**, and was never meant to be. Nixiesearch is a search index for consumer-facing apps to find top-N most relevant documents for a query. For analytical cases consider using good old SQL with [Clickhouse](https://github.com/ClickHouse/ClickHouse) or [Snowflake](https://www.snowflake.com/en/).
* **Not a tool to search for logs**. Log search is about throughput, and Nixiesearch is about relevance. If you plan to use Nixiesearch as a log storage system, please don't: consider [ELK](https://www.elastic.co/elastic-stack) or [Quickwit](https://github.com/quickwit-oss/quickwit) as better alternatives.

## The difference

> Our elasticsearch cluster has been a pain in the ass since day one with the main fix always "just double the size of the server" to the point where our ES cluster ended up costing more than our entire AWS bill pre-ES [ [HN source] ](https://news.ycombinator.com/item?id=30791838)

When your search cluster is red again when you accidentally send a wrong JSON to a wrong REST endpoint, you can just write your own S3-based search engine like big guys do:

* **Uber**:  [Lucene: Uber’s Search Platform Version Upgrade](https://www.uber.com/en-NL/blog/lucene-version-upgrade/).
* **Amazon**: [E-Commerce search at scale on Apache Lucene](https://www.youtube.com/watch?v=EkkzSLstSAE).
* **Doordash**: [Introducing DoorDash’s in-house search engine](https://careers.doordash.com/blog/introducing-doordashs-in-house-search-engine/).
![doordash design](img/doordash.gif)

Nixiesearch was inspired by these search engines, but is [open-source](#license). Decoupling search and storage makes ops simpler. Making your search configuration immutable makes it even more simple. 

![immutable config diagram](img/reindex.gif)

How it's different from popular search engines?

* vs [Elastic](https://www.elastic.co/elasticsearch): [Embedding inference](features/inference/embeddings.md), [hybrid search](features/search/overview.md#hybrid-search-with-reciprocal-rank-fusion) and [reranking](#) are free and open-source. For ES these are part of the [proprietary cloud](https://www.elastic.co/subscriptions/cloud).
* vs [OpenSearch](TODO): While OpenSearch can use [S3-based segment replication](https://opensearch.org/docs/latest/tuning-your-cluster/availability-and-recovery/segment-replication/index/), Nixiesearch can also offload cluster state to S3.
* vs [Qdrant](https://qdrant.tech/) and [Weaviate](https://weaviate.io/): Not a sidecar search engine to handle just vector search. [Autocomplete](features/autocomplete/index.md), [facets](features/search/facet.md), [RAG](features/search/rag.md) and [embedding inference](features/inference/embeddings.md) out of the box.


## Try it out

Get the sample [MSRD: Movie Search Ranking Dataset](https://github.com/metarank/msrd) dataset:

```shell
curl -o movies.jsonl.gz https://nixiesearch.ai/data/movies.jsonl
```

```text
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100   162  100   162    0     0   3636      0 --:--:-- --:--:-- --:--:--  3681
100 32085  100 32085    0     0   226k      0 --:--:-- --:--:-- --:--:--  226k
```

Create an index mapping for `movies` index in a file `config.yml`:

```yaml
inference:
  embedding:
    e5-small: # (1)
      model: intfloat/e5-small-v2 # (2)
schema:
  movies: # index name
    fields:
      title: # field name
        type: text
        search: 
          type: hybrid
          model: e5-small
        language: en # language is needed for lexical search
        suggest: true
      overview:
        type: text
        search: 
          type: hybrid
          model: e5-small
        language: en
```

1. We use [ONNX Runtime](https://onnxruntime.ai/) for local embedding inference. But you can also use any API-based SaaS embedding provider.
2. Any [SBERT](https://sbert.net/)-compatible embedding model can be used, and you can [convert your own](https://github.com/nixiesearch/onnx-convert)

Run the Nixiesearch [docker container](https://hub.docker.com/r/nixiesearch/nixiesearch):

```shell
docker run -itp 8080:8080 -v .:/data nixiesearch/nixiesearch:latest standalone -c /data/config.yml
```

```text
a.nixiesearch.index.sync.LocalIndex$ - Local index movies opened
ai.nixiesearch.index.Searcher$ - opening index movies
a.n.main.subcommands.StandaloneMode$ - ███╗   ██╗██╗██╗  ██╗██╗███████╗███████╗███████╗ █████╗ ██████╗  ██████╗██╗  ██╗
a.n.main.subcommands.StandaloneMode$ - ████╗  ██║██║╚██╗██╔╝██║██╔════╝██╔════╝██╔════╝██╔══██╗██╔══██╗██╔════╝██║  ██║
a.n.main.subcommands.StandaloneMode$ - ██╔██╗ ██║██║ ╚███╔╝ ██║█████╗  ███████╗█████╗  ███████║██████╔╝██║     ███████║
a.n.main.subcommands.StandaloneMode$ - ██║╚██╗██║██║ ██╔██╗ ██║██╔══╝  ╚════██║██╔══╝  ██╔══██║██╔══██╗██║     ██╔══██║
a.n.main.subcommands.StandaloneMode$ - ██║ ╚████║██║██╔╝ ██╗██║███████╗███████║███████╗██║  ██║██║  ██║╚██████╗██║  ██║
a.n.main.subcommands.StandaloneMode$ - ╚═╝  ╚═══╝╚═╝╚═╝  ╚═╝╚═╝╚══════╝╚══════╝╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝╚═╝  ╚═╝
a.n.main.subcommands.StandaloneMode$ -                                                                                
o.h.ember.server.EmberServerBuilder - Ember-Server service bound to address: [::]:8080
```

Build an index for a hybrid search:

```shell
curl -XPUT -d @movies.jsonl http://localhost:8080/movies/_index
```

```json
{"result":"created","took":8256}
```

Send the search query:

```shell
curl -XPOST -d '{"query": {"match": {"title":"matrix"}},"fields": ["title"], "size":3}'\
   http://localhost:8080/movies/_search
```

```json    
{
  "took": 1,
  "hits": [
    {
      "_id": "605",
      "title": "The Matrix Revolutions",
      "_score": 0.016666668
    },
    {
      "_id": "604",
      "title": "The Matrix Reloaded",
      "_score": 0.016393442
    },
    {
      "_id": "624860",
      "title": "The Matrix Resurrections",
      "_score": 0.016129032
    }
  ],
  "aggs": {},
  "ts": 1722441735886
}
```

You can also open `http://localhost:8080/_ui` in your web browser for a basic web UI:

![web ui](https://www.nixiesearch.ai/img/webui.png)

For more details, see a complete [Quickstart guide](quickstart.md).


## License

This project is released under the Apache 2.0 license, as specified in the [License](https://github.com/nixiesearch/nixiesearch/blob/master/LICENSE) file.

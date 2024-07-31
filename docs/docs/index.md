# Nixiesearch: batteries included search engine

[![CI Status](https://github.com/nixiesearch/nixiesearch/workflows/Tests/badge.svg)](https://github.com/nixiesearch/nixiesearch/actions)
[![License: Apache 2](https://img.shields.io/badge/License-Apache2-green.svg)](https://opensource.org/licenses/Apache-2.0)
![Last commit](https://img.shields.io/github/last-commit/nixiesearch/nixiesearch)
![Last release](https://img.shields.io/github/release/nixiesearch/nixiesearch)
[![Join our slack](https://img.shields.io/badge/Slack-join%20the%20community-blue?logo=slack&style=social)](https://communityinviter.com/apps/nixiesearch/nixiesearch)

## What is Nixiesearch?

Nixiesearch is a **hybrid search engine** that fine-tunes to your data. 

* Designed to be cloud-native with [S3-compatible index persistence](reference/config/persistence/s3.md). Distributed with stateless searchers and scale-to-zero. No more `status: red` on your cluster.
* Built on top of battle-tested [Apache Lucene](https://lucene.apache.org) library: [39 languages](reference/config/languages.md), [facets](reference/api/search/facet.md), [advanced filters](reference/api/search/filter.md), [autocomplete suggestions](reference/api/suggest.md) and [sorting](TODO) out of the box.
* Batteries included: fully local [RAG queries](concepts/search.md) and [vector search](reference/config/models.md) within a [single app](install.md). 
* Can learn the intent of a visitor by [fine-tuning an embedding model](https://github.com/nixiesearch/nixietune) to your data. Is "ketchup" relevant to a "tomato" query? It depends, but Nixiesearch can predict that from past user behavior.
> Want to learn more? Go straight to the [quickstart](https://www.nixiesearch.ai/quickstart/). 

### Why Nixiesearch?

Unlike Elastic/SOLR:

* Can run over [S3-compatible block storage](TODO): Rapid auto-scaling (even down to zero!) and much easier operations (your index is just a directory in S3 bucket!)
* [RAG](TODO),  [text](TODO) and [image](TODO) embeddings are first class search methods: no need for complex hand-written indexing pipelines.
* All LLM inference [can be run fully locally](TODO), no need to send all your queries and private documents to OpenAI API. But [you can](TODO), if you wish.

Unlike other vector search engines:

* **Supports [facets](reference/api/search/facet.md), [rich filtering](reference/api/search/filter.md), sorting and [autocomplete](reference/api/suggest.md)**: things you got used to in traditional search engines.
* **Text in, text out**: [text embedding](TODO) is handled by the search engine, not by you.
* **Exact-match search**: Nixiesearch is a hybrid retrieval engine searching over terms and embeddings. Your brand or SKU search queries will return what you expect, and not what the LLM hallucinates about.

The project is in active development and does not yet have backwards compatibility for configuration and data. Stay tuned and [reach out](https://www.metarank.ai/contact) if you want to try it!

### Why NOT Nixiesearch?

Nixiesearch has the following design limitations:

* **Does not support sharding**: sharding requires multi-node coordination and consensus, and we would like to avoid having any distributed state in the cluster - at least in the v1. If you plan to use Nixiesearch for searching 1TB of logs, please don't: consider [ELK](https://www.elastic.co/elastic-stack) or [Quickwit](https://github.com/quickwit-oss/quickwit) as better alternatives.
* **Query language is simple**: supporting analytical queries over deeply-nested documents is out of scope for the project. Nixiesearch is about consumer-facing search, and for analytical cases consider using [Clickhouse](https://github.com/ClickHouse/ClickHouse) or [Snowflake](https://www.snowflake.com/en/).

## Usage

Get the sample [MSRD: Movie Search Ranking Dataset](TODO) dataset:

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
schema:
  movies: # index name
    fields:
      title: # field name
        type: text
        search: hybrid
        language: en # language is needed for lexical search
        suggest: true
      overview:
        type: text
        search: hybrid
        language: en
```

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

You can also open `http://localhost:8080/movies/_ui` in your web browser for a basic web UI:

![web ui](https://www.nixiesearch.ai/img/webui.png)

For more details, see a complete [Quickstart guide](https://www.nixiesearch.ai/quickstart).

## Design

Nixiesearch is inspired by an Amazon search engine design described in a talk
[E-Commerce search at scale on Apache Lucene](https://www.youtube.com/watch?v=EkkzSLstSAE):

![NS design diagram](https://www.nixiesearch.ai/img/arch.png)

Compared to traditional search engines like Elasticsearch/Solr:

* **Independent stateful indexer and stateless search backends**: with index sync happening via S3-compatible block storage.
  No more red index status and cluster split-brains due to indexer overload.
* **Pull-based indexing**: pull updated documents right from [Kafka](https://kafka.apache.org/) in real-time, no need for separate indexing ETL jobs with limited throughput.

Nixiesearch uses [RRF](https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf) for combining text and neural search results.

## Limitations

Nixiesearch is not a general-purpose search engine like Elasticsearch:

* **No sharding support** (yet): so it's not made for logs and APM data. Indices up to 5M docs are OK.
* **May require GPU**: computing embeddings for large search corpora during indexing is a compute-intensive task and may take
  a lot of resources if run on CPU. And GPU is a must for a model fine-tuning.

## Current status

At the moment, Nixiesearch is in the process of active development, so please reach out to use via [the contact](https://www.metarank.ai/contact) form if you want to try it!

- [x] Search/Index API: 
- [x] Index mapping
- [x] Config file parsing
- [x] Lexical search with Lucene
- [x] Semantic search with Lucene
- [x] Hybrid search
- [x] MSMARCO E2E hybrid test
- [x] Facets for terms and ranges
- [x] Boolean filtering for lexical/semantic search
- [x] Autocomplete suggestions
- [x] S3 index sync
- [x] LLM fine-tuning
- [ ] Cut-off threshold prediction for semantic search
- [ ] Sorting support
- [ ] Swagger/OpenAPI schema
- [x] Nice webui
- [x] Pull-based indexing from file/Kafka

License
=====
This project is released under the Apache 2.0 license, as specified in the [License](LICENSE) file.

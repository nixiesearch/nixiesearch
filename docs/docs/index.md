# Nixiesearch: neural search engine for the rest of us

[![CI Status](https://github.com/nixiesearch/nixiesearch/workflows/Tests/badge.svg)](https://github.com/nixiesearch/nixiesearch/actions)
[![License: Apache 2](https://img.shields.io/badge/License-Apache2-green.svg)](https://opensource.org/licenses/Apache-2.0)
![Last commit](https://img.shields.io/github/last-commit/nixiesearch/nixiesearch)
![Last release](https://img.shields.io/github/release/nixiesearch/nixiesearch)
[![Join our slack](https://img.shields.io/badge/Slack-join%20the%20community-blue?logo=slack&style=social)](https://communityinviter.com/apps/nixiesearch/nixiesearch)

## What is Nixiesearch?

Nixiesearch is a hybrid search engine that fine-tunes to your data. 

* Can learn the intent of a visitor by [fine-tuning an embedding model](https://github.com/nixiesearch/nixietune) to your data. Is "ketchup" relevant to a "tomato" query? It depends, but Nixiesearch can predict that from past user behavior.
* Built on top of battle-tested [Apache Lucene](https://lucene.apache.org) library: [39 languages](reference/config/languages.md), [facets](reference/api/search/facet.md), [advanced filters](reference/api/search/filter.md), [autocomplete suggestions](reference/api/suggest.md) and [sorting](TODO) out of the box.
* Designed to be cloud-native with [S3/blockstore index persistence](TODO). Distributed with stateless searchers and scale-to-zero. No more `status: red` on your cluster.

> Want to learn more? Go straight to the [quickstart](https://www.nixiesearch.ai/quickstart/). 

### Why Nixiesearch?

Unlike some of the other vector search engines:

* **Supports facets, rich boolean filtering, sorting and autocomplete**: things you got used to in traditional search engines.
* **Text in, text out**: LLM embedding is handled by the search engine, not by you.
* **Exact-match search**: Nixiesearch is a hybrid retrieval engine searching over terms and embeddings. Your brand or SKU search queries will return what you expect, and not what the LLM hallucinates about.

The project is in active development and not intended for production use *just yet*. Stay tuned and [reach out](https://www.metarank.ai/contact) if you want to try it!

### Why NOT Nixiesearch?

Nixiesearch has the following design limitations:

* **Does not support sharding**: sharding requires multi-node coordination and consensus, and we would like to avoid having any distributed state in the cluster - at least in the v1. If you plan to use Nixiesearch for searching 1TB of logs, please don't: consider [ELK](https://www.elastic.co/elastic-stack) or [Quickwit](https://github.com/quickwit-oss/quickwit) as better alternatives.
* **Query language is simple**: supporting analytical queries over deeply-nested documents is out of scope for the project. Nixiesearch is about search, and for analytical databases consider using [Clickhouse](https://github.com/ClickHouse/ClickHouse) or [Snowflake](https://www.snowflake.com/en/).

## Usage

Get the sample [MS MARCO](https://microsoft.github.io/msmarco/) dataset:

```shell
curl -L -O http://nixiesearch.ai/data/msmarco.json
```

```text
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100   162  100   162    0     0   3636      0 --:--:-- --:--:-- --:--:--  3681
100 32085  100 32085    0     0   226k      0 --:--:-- --:--:-- --:--:--  226k
```

Run the Nixiesearch [docker container](https://hub.docker.com/r/nixiesearch/nixiesearch):

```shell
docker run -i -t -p 8080:8080 nixiesearch/nixiesearch:latest standalone
```

```text
12:40:47.325 INFO  ai.nixiesearch.main.Main$ - Staring Nixiesearch
12:40:47.460 INFO  ai.nixiesearch.config.Config$ - No config file given, using defaults
12:40:47.466 INFO  ai.nixiesearch.config.Config$ - Store: LocalStoreConfig(LocalStoreUrl(/))
12:40:47.557 INFO  ai.nixiesearch.index.IndexRegistry$ - Index registry initialized: 0 indices, config: LocalStoreConfig(LocalStoreUrl(/))
12:40:48.253 INFO  o.h.blaze.server.BlazeServerBuilder - 
███╗   ██╗██╗██╗  ██╗██╗███████╗███████╗███████╗ █████╗ ██████╗  ██████╗██╗  ██╗
████╗  ██║██║╚██╗██╔╝██║██╔════╝██╔════╝██╔════╝██╔══██╗██╔══██╗██╔════╝██║  ██║
██╔██╗ ██║██║ ╚███╔╝ ██║█████╗  ███████╗█████╗  ███████║██████╔╝██║     ███████║
██║╚██╗██║██║ ██╔██╗ ██║██╔══╝  ╚════██║██╔══╝  ██╔══██║██╔══██╗██║     ██╔══██║
██║ ╚████║██║██╔╝ ██╗██║███████╗███████║███████╗██║  ██║██║  ██║╚██████╗██║  ██║
╚═╝  ╚═══╝╚═╝╚═╝  ╚═╝╚═╝╚══════╝╚══════╝╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝╚═╝  ╚═╝
                                                                               
12:40:48.267 INFO  o.h.blaze.server.BlazeServerBuilder - http4s v1.0.0-M38 on blaze v1.0.0-M38 started at http://0.0.0.0:8080/
```

Build an index for a hybrid search:

```shell
curl -XPUT -d @msmarco.json http://localhost:8080/msmarco/_index
```

```json
{"result":"created","took":8256}
```

Send the search query:

```shell
curl -XPOST -d '{"query": {"match": {"text":"new york"}},"fields": ["text"]}'\
    http://localhost:8080/msmarco/_search
```

```json
{
  "took": 13,
  "hits": [
    {
      "_id": "8035959",
      "text": "Climate & Weather Averages in New York, New York, USA.",
      "_score": 0.016666668
    },
    {
      "_id": "2384898",
      "text": "Consulate General of the Republic of Korea in New York.",
      "_score": 0.016393442
    },
    {
      "_id": "2241745",
      "text": "This is a list of the tallest buildings in New York City.",
      "_score": 0.016129032
    }
}
```

You can also open `http://localhost:8080/_ui` in your web browser for a basic web UI:

![web ui](https://www.nixiesearch.ai/img/webui.png)

For more details, see a complete [Quickstart guide](https://www.nixiesearch.ai/quickstart).

## Design

Nixiesearch is inspired by an Amazon search engine design described in a talk
[E-Commerce search at scale on Apache Lucene](https://www.youtube.com/watch?v=EkkzSLstSAE):

![NS design diagram](https://www.nixiesearch.ai/img/arch.png)

Compared to traditional search engines like Elasticsearch/Solr:

* **Independent stateful indexer and stateless search backends**: with index sync happening via S3-compatible block storage.
  No more red index status and cluster split-brains due to indexer overload.
* **Pull-based indexing**: pull updated documents right from [Kafka](https://kafka.apache.org/) in real-time, no need for
  separate indexing ETL jobs with limited throughput.

Nixiesearch uses [RRF](https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf) for combining text and neural search results.

## Limitations

Nixiesearch is not a general-purpose search engine like Elasticsearch:

* **No sharding support** (yet): so it's not made for logs and APM data. Indices up to 5M docs are OK.
* **May require GPU**: computing embeddings for large search corpora during indexing is a compute-intensive task and may take
  a lot of resources if run on CPU. And GPU is a must for a model fine-tuning.

## Current status

At the moment, Nixiesearch is in the process of active development, so please reach out to use via [the contact](https://www.metarank.ai/contact) form if you want to try it!

- [x] Search/Index API
- [x] Index mapping
- [x] Config file parsing
- [x] Lexical search with Lucene
- [x] Semantic search with Lucene
- [x] Hybrid search
- [x] MSMARCO E2E hybrid test
- [x] Facets for terms and ranges
- [x] Boolean filtering for lexical/semantic search
- [x] Autocomplete suggestions
- [ ] S3 index sync
- [ ] LLM fine-tuning
- [ ] Cut-off threshold prediction for semantic search

License
=====
This project is released under the Apache 2.0 license, as specified in the [License](LICENSE) file.

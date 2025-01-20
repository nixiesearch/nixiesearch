# Quickstart

This guide will show you how to run Nixiesearch in a [standalone](deployment/standalone.md) mode on your local machine using Docker. We will:

* start Nixiesearch in a [standalone](deployment/standalone.md) mode using Docker
* [index](features/indexing/index.md) a demo set of documents using [REST API](features/indexing/api.md)
* run a couple of [search queries](features/search/query.md)

## Prerequisites

This guide assumes that you already have the following available:

* Docker: [Docker Desktop](https://docs.docker.com/engine/install/) for Mac/Windows, or Docker for Linux.
* Operating system: Linux, macOS, Windows with [WSL2](https://learn.microsoft.com/en-us/windows/wsl/install).
* Architecture: x86_64 or AArch64.
* Memory: 2Gb dedicated to Docker.

## Getting the dataset

For this guide we'll use a [MSRD: Movie Search Ranking Dataset](https://github.com/metarank/msrd), which contains textual, categorical and numerical information for each document. Dataset is hosted on Huggingface at [nixiesearch/demo-datasets](https://huggingface.co/datasets/nixiesearch/demo-datasets), each document contains following fields:

```json
{
  "_id": "27205",
  "title": "Inception",
  "overview": "Cobb, a skilled thief who commits corporate espionage by infiltrating the subconscious of his targets is offered a chance to regain his old life as payment for a task considered to be impossible: inception, the implantation of another person's idea into a target's subconscious.",
  "tags": [
    "alternate reality",
    "thought-provoking",
    "visually appealing"
  ],
  "genres": [
    "action",
    "crime",
    "drama"
  ],
  "director": "Christopher Nolan",
  "actors": [
    "Tom Hardy",
    "Cillian Murphy",
    "Leonardo DiCaprio"
  ],
  "characters": [
    "Eames",
    "Robert Fischer",
  ],
  "year": 2010,
  "votes": 32606,
  "rating": 8.359,
  "popularity": 91.834,
  "budget": 160000000,
  "img_url": "https://image.tmdb.org/t/p/w154/8IB2e4r4oVhHnANbnm7O3Tj6tF8.jpg"
}

```

You can download the dataset and unpack it with the following command:

```shell
$> curl -L -o movies.jsonl https://nixiesearch.ai/data/movies.jsonl 
```

## Index schema

Unlike other search engines, Nixiesearch requires a strongly-typed description of all the fields of documents you plan to index. As we know that our movie documents from the demo dataset have `title`, `description` and some other fields, let's define a `movies` index in a file `config.yml`:

```yaml
inference:
  embedding:
    e5-small:
      provider: onnx
      model: nixiesearch/e5-small-v2-onnx
      prompt:
        query: "query: "
        doc: "passage: "
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
      genres:
        type: text[]
        filter: true
        facet: true
      year:
        type: int
        filter: true
        facet: true
```

!!! note 

    Each document field definition **must have a type**. Schemaless dynamic mapping is considered an anti-pattern, as the search engine must know beforehand which structure to use for the index. [int, float, long, double, text, text[], bool](features/indexing/types/index.md) field types are currently supported.

See a full [index mapping reference](features/indexing/mapping.md) for more details on defining indexes, and [ML inference](features/inference/index.md) on configuring ML models inside Nixiesearch for CPU and [GPU](deployment/gpu.md). 

## Starting the service

Nixiesearch is distributed as a Docker container, which can be run with the following command:
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

Options breakdown:

* `-i` and `-t`: interactive docker mode with allocated TTY. Useful when you want to be able to press Ctrl-C to stop the application.
* `-p 8080:8080`: expose the port 8080.
* `-v .:/data`: mount current dir (with a `config.yml` file!) as a `/data` inside the container 
* `standalone`: a Nixiesearch running mode, with colocated indexer and searcher processes.
* `-c /data/config.yml`: use a config file with `movies` index mapping

!!! note

    Standalone mode is designed for small-scale and development deployments: it uses local filesystem for index storage, and runs both indexer and searcher within a single application. For production usage please consider a [distributed mode](deployment/distributed/index.md) over S3-compatible block storage.

## Indexing data

After you start the Nixiesearch service in the `standalone` mode listening on port `8080`, let's index some docs with [REST API](features/indexing/api.md)!

Nixiesearch uses a similar API semantics as Elasticsearch, so to upload docs for indexing, you need to make a HTTP PUT request to the `/<index-name>/_index` endpoint:

```shell
curl -XPUT -d @movies.jsonl http://localhost:8080/movies/_index
```

```json
{"result":"created","took":8256}
```

As Nixiesearch is running an LLM embedding model inference inside, indexing large document corpus on CPU may take a while.

!!! note

    Nixiesearch can also index documents directly from a [local file](deployment/distributed/indexing/file.md), [S3 bucket](deployment/distributed/indexing/file.md) or [Kafka topic](deployment/distributed/indexing/kafka.md) in a pull-based scenario. Both in realtime and offline. Check [Building index](features/indexing/index.md) reference for more information about indexing your data.

## Sending search requests

Query DSL in Nixiesearch is inspired but not compatible with the JSON syntax used in [Elasticsearch](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html)/[OpenSearch](https://opensearch.org/docs/latest/query-dsl/index/) Query DSL. 

To perform a single-field hybrid search over our newly-created `movies` index, run the following cURL command:

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

This query performed a hybrid search:

* for lexical search, it built and executed a Lucene query of `title:matrix`
* for semantic search, it computed an LLM embedding of the query `matrix` and performed a-kNN search over document embeddings, stored in [Lucene HNSW index](https://lucene.apache.org/core/9_1_0/core/org/apache/lucene/util/hnsw/HnswGraphSearcher.html).
* combined results of both searches into a single ranking with the [Reciprocal Rank Fusion](https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf).

!!! note

    Learn more about searching in the [Search](features/search/index.md) section.

## Web UI

Nixiesearch has a basic search web UI available as `http://localhost:8080/_ui` URL.

![web ui](https://www.nixiesearch.ai/img/webui.png)

## Next steps

If you want to continue learning about Nixiesearch, these sections of documentation are great next steps:

* [An overview of Nixiesearch design](index.md#design) to understand how it differs from existing search engines.
* Using [Filters](features/search/index.md#filters) and [Facets](features/search/index.md#facets) while searching.
* [How it should be deployed](deployment/index.md) in a production environment.
* Building [semantic autocomplete](features/autocomplete/index.md) index for search-as-you-type support.

If you have a question not covered in these docs and want to chat with the team behind Nixiesearch, you're welcome to join our [Community Slack](https://communityinviter.com/apps/nixiesearch/nixiesearch)

# Quickstart

This guide will show you how to run Nixiesearch in a [standalone](deployment/standalone.md) mode on your local machine using Docker. We will:

* start Nixiesearch in a [standalone](deployment/standalone.md) mode using Docker
* [index](features/indexing/overview.md) a demo set of documents using [REST API](api.md)
* run a couple of [search queries](features/search/query/overview.md)

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
curl -L -o movies.jsonl https://nixiesearch.ai/data/movies.jsonl 
```

## Index schema

Unlike other search engines, Nixiesearch requires a strongly-typed description of all the fields of documents you plan to index. As we know that our movie documents from the demo dataset have `title`, `description` and some other fields, let's define a `movies` index in a file `config.yml`:

```yaml
inference:
  embedding:
    e5-small:
      model: intfloat/e5-small-v2
schema:
  movies: # index name
    fields:
      title: # field name
        type: text
        search:
          lexical: # build lexical index
            analyze: english
          semantic: # and a vector search index also
            model: e5-small
        suggest: true
      overview:
        type: text
        search: false
```

This YAML file defines:
* an embedding model `e5-small` with a HuggingFace model `intfloat/e5-small-v2`
* a single index `movies` with a text field `title` configured for both lexical and semantic search.

!!! note "External Embedding Computation"

    You don't need to configure local embedding inference if you prefer to compute embeddings outside of Nixiesearch. Instead of the `model` parameter, you can use the `dim` parameter and provide pre-computed embeddings with your documents. See [text fields documentation](features/indexing/types/text.md#server-side-vs-pre-embedded-documents) for details.

!!! note

    Each document field definition **must have a type**. Schemaless dynamic mapping is considered an anti-pattern, as the search engine must know beforehand which structure to use for the index. [int, float, long, double, text, text[], bool](features/indexing/types/overview.md) field types are currently supported.

See a full [index mapping reference](features/indexing/mapping.md) for more details on defining indexes, and [ML inference](features/inference/overview.md) on configuring ML models inside Nixiesearch for CPU and [GPU](deployment/distributed/gpu.md).

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

    Standalone mode is designed for small-scale and development deployments: it uses local filesystem for index storage, and runs both indexer and searcher within a single application. For production usage please consider a [distributed mode](deployment/distributed/overview.md) over S3-compatible block storage.

## Indexing data

After you start the Nixiesearch service in the `standalone` mode listening on port `8080`, let's index some docs with [REST API](api.md)!

Nixiesearch uses a similar API semantics as Elasticsearch, so to upload docs for indexing, you need to make a HTTP PUT request to the `/<index-name>/_index` endpoint:

```shell
curl -XPOST -d @movies.jsonl http://localhost:8080/v1/index/movies
```

```json
{"result":"created","took":8256}
```

As Nixiesearch is running a local embedding model inference inside, indexing large document corpus on CPU may take a while. Optionally you can use API-based embedding providers like [OpenAI](features/inference/embeddings/openai.md) and [Cohere](features/inference/embeddings/cohere.md).

!!! note

    Nixiesearch can also index documents directly from a [local file](deployment/distributed/indexing/file.md), [S3 bucket](deployment/distributed/indexing/file.md) or [Kafka topic](deployment/distributed/indexing/kafka.md) in a pull-based scenario. Both in realtime and offline. Check [Building index](features/indexing/overview.md) reference for more information about indexing your data.

## Sending search requests

Query DSL in Nixiesearch is inspired but not compatible with the JSON syntax used in [Elasticsearch](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html)/[OpenSearch](https://opensearch.org/docs/latest/query-dsl/index/) Query DSL.

To perform a single-field lexical search over our newly-created `movies` index, run the following cURL command:

```shell
curl -XPOST http://localhost:8080/v1/index/movies/search \
  -H "Content-Type: application/json" \
  -d '{ 
    "query": { 
      "match": { 
        "title": "batman" 
      } 
    }, 
    "fields": ["title"], 
    "size": 5
  }'
```

You will get the following response:

```json
{
  "took": 1,
  "hits": [
    {
      "_id": "414906",
      "title": "The Batman",
      "_score": 3.0470526
    },
    {
      "_id": "272",
      "title": "Batman Begins",
      "_score": 2.4646688
    },
    {
      "_id": "324849",
      "title": "The Lego Batman Movie",
      "_score": 2.0691848
    },
    {
      "_id": "209112",
      "title": "Batman v Superman: Dawn of Justice",
      "_score": 1.5664694
    }
  ],
  "aggs": {},
  "ts": 1745590547587
}
```

Let's go deeper and perform hybrid search query, by mixing lexical (using [match query](features/search/query/retrieve/match.md)) and semantic (using [semantic query](features/search/query/retrieve/semantic.md)) with [Reciprocal Rank Fusion](https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf):

```shell
curl -XPOST http://localhost:8080/v1/index/movies/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "rrf": {
        "retrieve": [
          { "match": { "title": "batman" } },
          { "semantic": { "title": "batman nolan" } }
        ],
        "rank_window_size": 20
      }
    },
    "fields": ["title"],
    "size": 5
  }'
```

And we also got a "The Dark Knight" movie!

```json
{
  "took": 8,
  "hits": [
    {
      "_id": "414906",
      "title": "The Batman",
      "_score": 0.033333335
    },
    {
      "_id": "272",
      "title": "Batman Begins",
      "_score": 0.032786883
    },
    {
      "_id": "209112",
      "title": "Batman v Superman: Dawn of Justice",
      "_score": 0.031257633
    },
    {
      "_id": "324849",
      "title": "The Lego Batman Movie",
      "_score": 0.031054404
    },
    {
      "_id": "155",
      "title": "The Dark Knight",
      "_score": 0.016129032
    }
  ],
  "aggs": {},
  "ts": 1745590503193
}
```

This query performed a hybrid search:

* for lexical search, it built and executed a Lucene query of `title:batman`
* for semantic search, it computed an LLM embedding of the query `batman nolan` and performed a-kNN search over document embeddings, stored in [Lucene HNSW index](https://lucene.apache.org/core/9_1_0/core/org/apache/lucene/util/hnsw/HnswGraphSearcher.html).
* combined results of both searches into a single ranking with the [Reciprocal Rank Fusion](https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf).

!!! note

    Learn more about searching in the [Search](features/search/overview.md) section.

## Next steps

If you want to continue learning about Nixiesearch, these sections of documentation are great next steps:

* [An overview of Nixiesearch design](index.md#the-difference) to understand how it differs from existing search engines.
* Using [Filters](features/search/overview.md#filters) and [Facets](features/search/overview.md#facets) while searching.
* [How it should be deployed](deployment/overview.md) in a production environment.
* Building [semantic autocomplete](features/autocomplete/index.md) index for search-as-you-type support.

If you have a question not covered in these docs and want to chat with the team behind Nixiesearch, you're welcome to join our [Community Slack](https://communityinviter.com/apps/nixiesearch/nixiesearch)

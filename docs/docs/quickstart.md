# Quickstart

This guide will show you how to run Nixiesearch in a [standalone](TODO) mode on your local machine using Docker. We will:

* start Nixiesearch in [standalone](TODO) mode using Docker
* [index](TODO) a demo set of documents using [REST API](todo)
* run a couple of [search queries](TODO)

## Prerequisites

This guide assumes that you already have the following available:

* Docker: [Docker Desktop](https://docs.docker.com/engine/install/) for Mac/Windows, or Docker for Linux.
* Operating system: Linux, macOS, Windows with [WSL2](https://learn.microsoft.com/en-us/windows/wsl/install).
* Architecture: x86_64 or AArch64.
* Memory: 2Gb dedicated to Docker.

## Getting the dataset

For this guide we'll use a [MSRD: Movie Search Ranking Dataset](TODO), which contains textual, categorical and numerical information for each document. Dataset is hosted on Huggingface at [nixiesearch/demo-datasets](https://huggingface.co/datasets/nixiesearch/demo-datasets), each document contains following fields:

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
$> curl -fLo movies.jsonl.gz https://huggingface.co/datasets/nixiesearch/demo-datasets/resolve/main/data/movies.jsonl.gz

  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100  1206  100  1206    0     0   4576      0 --:--:-- --:--:-- --:--:--  4585
100  317k  100  317k    0     0   783k      0 --:--:-- --:--:-- --:--:--  783k

$> gzip -d movies.jsonl.gz

$> ls -l movies.jsonl 
-rw-r--r-- 1 user user 838910 Jul 31 14:55 movies.jsonl
```

## Index schema

Unlike other search engines, Nixiesearch requires a strongly-typed description of all the fields of documents you plan to index. As we know that our movie documents from the demo dataset have title and description fields

## Starting the service

Nixiesearch is distributed as a Docker container, which can be run with the following command:
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

Options breakdown:

* `-i` and `-t`: interactive docker mode with allocated TTY. Useful when you want to be able to press Ctrl-C to stop the application.
* `-p 8080:8080`: expose the port 8080.
* `standalone`: a Nixiesearch running mode, with colocated indexer and searcher processes.

> **Note**: Standalone mode in Nixiesearch is made for testing and does not need a config file. But this mode has its own trade-offs:
> 
> * When started from Docker, there is **no persistence**: all your indexed documents are stored in RAM and will be lost after restart. See the [Persistence](reference/config/persistence/overview.md) chapter on setting it up.
> * **Dynamic mapping** is enabled: Nixiesearch will try to deduce index schema based on documents it indexes. As it cannot know upfront which fields are you going to use for search, filtering and faceting, it marks every field as searchable, filterable and facetable - which wastes a lot of disk space. See the [Index mapping](reference/config/mapping.md) section for details.

## Indexing data

After you start the Nixiesearch service in the `standalone` mode listening on port `8080`, let's index some docs!

> **Note**: Nixiesearch does not require you to have an explicitly defined index schema and can generate it on the fly. 
>
> In this quickstart guide we will skip creating explicit index mapping, but it is always a good idea to have it prior to indexing. See [Mapping](reference/config/mapping.md) section for more details. 

Nixiesearch uses a similar API semantics as Elasticsearch, so to upload docs for indexing, you need to make a HTTP PUT request to the `/<index-name>/_index` endpoint:

```shell
curl -XPUT -d @msmarco.json http://localhost:8080/msmarco/_index
```

```json
{"result":"created","took":8256}
```

As Nixiesearch is running an LLM embedding model inference inside, indexing large document corpus on CPU may take a while.

> Check [Building index](concepts/indexing.md) for more information about indexing your data.

## Index mapping

As we used dynamic mapping generation based on indexed documents, you may be curious how the resulting mapping looks. You can see it with the following API call:
```shell
curl http://localhost:8080/msmarco/_mapping
```

```json
{
  "name": "msmarco",
  "alias": [],
  "config": {
    "mapping": {
      "dynamic": true
    }
  },
  "fields": {
    "_id": {
      "type": "text",
      "name": "_id",
      "search": {
        "type": "disabled"
      },
      "store": true,
      "sort": false,
      "facet": false,
      "filter": true
    },
    "text": {
      "type": "text",
      "name": "text",
      "search": {
        "type": "hybrid",
        "model": "nixiesearch/e5-small-v2-onnx",
        "prefix": {
          "query": "query: ",
          "document": "passage: "
        },
        "language": "english"
      },
      "store": true,
      "sort": true,
      "facet": true,
      "filter": true
    }
  }
}
```

As you can see, Nixiesearch made some indexing decisions, which may be not optimal for production use. 

The `text` type field is by default:

* marked as `sort: true`. Building a sorted field requires constructing a separate Lucene `DocValues` field, which is usually kept in RAM while searching.
* used for full-text search with `search.type: hybrid`. Using hybrid/semantic search fields requires running a LLM inference on indexing, which may take a lot of CPU resources.

In production deployments we highly advise using the explicit index mapping. For more details, see [Index mapping](reference/config/mapping.md) section of documentation.

## Sending requests

Query DSL in Nixiesearch is inspired but not compatible with the JSON syntax used in [Elasticsearch](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html)/[OpenSearch](https://opensearch.org/docs/latest/query-dsl/index/) Query DSL. 

To perform a single-field hybrid search over our newly-created `msmarco` index, run the following cURL command:

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

This query performed a hybrid search:

* for lexical search, it built and executed a Lucene query of `text:new text:york`
* for semantic search, it computed an LLM embedding of the query `new york` and performed a-kNN search over document embeddings, stored in [Lucene HNSW index](https://lucene.apache.org/core/9_1_0/core/org/apache/lucene/util/hnsw/HnswGraphSearcher.html).
* combined results of both searches into a single ranking with the [Reciprocal Rank Fusion](https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf).

> Learn more about searching in the [Search](concepts/search.md) section.

## Web UI

Nixiesearch has a basic search web UI available as `http://localhost:8080/_ui` URL.

![web ui](https://www.nixiesearch.ai/img/webui.png)

## Next steps

If you want to continue learning about Nixiesearch, these sections of documentation are great next steps:

* [An overview of Nixiesearch design](concepts/difference.md) to understand how it differs from existing search engines.
* Using [Filters](concepts/search.md#filters) and [Facets](concepts/search.md#facets) while searching.
* [How it should be deployed](concepts/deploy.md) in a production environment.
* Building [semantic autocomplete](concepts/autocomplete.md) index for search-as-you-type support.

If you have a question not covered in these docs and want to chat with the team behind Nixiesearch, you're welcome to join our [Community Slack](https://communityinviter.com/apps/nixiesearch/nixiesearch)
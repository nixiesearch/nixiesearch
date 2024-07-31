# Building an index

Nixiesearch index is a searchable group of documents sharing the same structure.

To add a set of documents to an index, you need to perform two steps:

* define an [index mapping](#index-mapping) [statically in a config file](#static-index-mapping). Nixiesearch is a strongly-typed document storage system, so dynamic mapping is not supported.
* write documents to the index, either with push-based REST API or with pull-based stream ingestion.

!!! note 

    Dynamic mapping in most search engines is considered an anti-pattern: the engine cannot correctly guess how are you going to query documents, so by default all fields are marked as searchable, facetable, filterable and suggestable. This results in slow ingestion throughput and huge index size.

## Index mapping

To define an index mapping, you need to add an index-specific block to the `schema` section of the [configuration file](../reference/config/mapping.md):

```yaml
schema:
  my-first-index:
    fields:
      title:
        type: text
        search: lexical
        language: en
      price:
        type: float
        filter: true
```

In the example above we defined an index `my-first-index` with two fields title and price. Index is stored on disk by default.

Each field definition in a static mapping has two groups of settings:

* Field type specific parameters - like how it's going to be searched for text fields.
* Global parameters - is this field filterable, facetable and sortable.

Go to [the mapping reference](../reference/config/mapping.md) section for more details on all parameters.

## Writing documents to an index

Internally Nixiesearch implements a pull-based indexing - the service itself asks for a next chunk of documents from an upstream system.

![push pull](../../img/pullpush.png)

For convenience, Nixiesearch can emulate a push-based approach via [REST API](todo) - your app should send a payload with documents and wait for an acknowledgement.

### Starting Nixiesearch

Nixiesearch has multiple ways of running indexing:

* [Offline indexing](../reference/cli/index.md#offline-indexing). Useful when performing full reindexing from static document source, like from a set of files, or from Kafka topic.
* [Online indexing](../reference/cli/index.md#online-indexing). For folks who got used to Elasticsearch with REST API.

For the sake of simplicity we can start Nixiesearch in a `standalone` mode, which bundles both searcher and indexer in a single process with a shared [REST API](../reference/api/overview.md).

```shell
docker run -it nixiesearch/nixiesearch:latest standalone --config /path/to/conf.yml
```

### Indexing REST API

Each Nixiesearch index has an `_index` REST endpoint where you can [HTTP PUT](https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/PUT) your documents to.

This endpoint expects a JSON payload in [one of the following formats](../reference/api/index/document-format.md):

* JSON object: just a single document.
* JSON array of objects: a batch of documents.
* JSON-Line array of objects: also a batch of documents, but simpler wire format.

For example, writing a single document to an `dev` index can be done with a cURL command:

```bash
curl -XPUT -d '{"title": "hello", "color": ["red"], "meta": {"sku":"a123"}}'\
  http://localhost:8080/dev/_index
```

!!! warning

    As Nixiesearch deliberately has no indexing queue, it asynchronously blocks the response till all the documents in the submitted batch were indexed. You should avoid doing HTTP PUT's with too large payloads and instead split them into smaller batches of 100-500 documents.

!!! note

    To have proper back-pressure mechanism, prefer using a pull-based indexing with [Apache Kafka](../deployment/kafka.md) or with [offline file-based ingestion](../reference/cli/index.md#offline-indexing).

### Streaming document indexing

With pull-based streaming indexing supported natively, it becomes trivial to implement these typical scenarios:

1. **Batch full re-indexing**: take all documents from a datasource and periodically re-build index from scratch.
2. **Distributed journal as a single source of truth**: use [Kafka compacted topics](https://developer.confluent.io/courses/architecture/compaction/) as a view over last versions of documents, with real-time updates.
3. **Large dataset import**: import a complete set of documents from local/S3 files, maintaining optimal throughput and batching.

![kafka streaming](../../img/kafka.png)

Nixiesearch supports [Apache Kafka](https://kafka.apache.org/), [AWS S3](https://aws.amazon.com/s3/) (and also compatible object stores) and local files as a source of documents for indexing.

If you have your dataset in a JSON file, instead of making HTTP PUT with very large payload using REST API, you can invoke a [`nixiesearch index`](../reference/cli/index.md) sub-command to perform streaming indexing in a separate process:

```shell
docker run -i -t -v <your-local-dir>:/data nixiesearch/nixiesearch:latest index file\
  --config /data/conf.yml --index <index name> --url file:///data/docs.json
```

Where `<your-local-dir>` is a directory containing the `conf.yml` config file and a `docs.json` with documents for indexing. See [index CLI reference](../reference/cli/index.md) and [Supported URL formats](../reference/config/url.md) for more details.
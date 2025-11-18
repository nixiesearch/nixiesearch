# Text fields

Unlike other Lucene-based search engines, Nixiesearch has [a distinction between singular and repeated](../format.md#repeated-fields) fields on a [schema](../mapping.md) level - so choose your field type wisely:

* for singular fields, use the `text` type.
* for repeated fields, choose the `text[]` type.

Example field schema for a text fields `title` and `genre`:

```yaml
schema:
  movies:
    fields:
      title:
        type: text      # only a single title is allowed
        search:
          semantic:
            model: e5-small
      genre:
        type: text[]    # there can be multiple genres per document
        search: 
          lexical:
            analyze: english
        filter: true    # field is filterable
        facet: true     # field is facetable
        store: true     # can retrieve the field back from index
        suggest: true   # build autocomplete suggestions based on that field
```

## Semantic index parameters

When a text field has a semantic search enabled, there are a couple of parameters you can further configure:

```yaml
schema:
  movies:
    fields:
      title:
        type: text      # only a single title is allowed
        search:
          semantic:
            model: e5-small  # optional: for server-side inference
            dim: 384         # optional: for pre-embedded documents
            ef: 32
            m: 16
            quantize: float32
            workers: 4
            distance: dot
```

Fields:

* `model` (optional): embedding model for server-side inference. Required when documents don't have pre-computed embeddings.
* `dim` (optional): embedding vector dimensions for pre-embedded documents. Required when `model` is not specified. Maximum supported dimensions: 8192.
* `ef` and `m`: HNSW index parameters. The higher these values, the better the search recall at the cost of performance.
* `quantize` (optional, `float32`/`int8`/`int4`/`int1`, default `float32`): index quantization level. `int8` saves 4x RAM and disk but at the cost of worse recall.
* `workers` (optional, int, default is same as number of CPUs in the system): how many background workers to use for HNSW indexing operations.
* `distance` (optional, `dot`/`cosine`, default `dot`): which embedding distance function to use. `dot` is faster (and mathematically equals to `cosine`) if your embeddings are normalized (see [embedding inference](../../inference/embeddings.md#configuration-file) section for details)

### Server-side vs Pre-embedded documents

Nixiesearch supports two modes for semantic search:

1. **Server-side inference**: Use the `model` parameter to compute embeddings on the server
2. **Pre-embedded documents**: Use the `dim` parameter when documents already contain embedding vectors

You must specify either `model` or `dim`, but not both. 

## Operations on text fields

### Document ingestion format

When a document with a `text` field is ingested, Nixiesearch expects the document JSON payload for the field to be in either format:

* `JSON string`: like `{"title":"cookies"}`, when text embedding is computed by the server
* `JSON obj`: like `{"title": {"text":"cookies", "embedding": [1,2,3]}}` for pre-embedded documents. 

See [pre-embedded text fields](../format.md#pre-embedded-text-fields) in the [Document format](../format.md) section for more details.

### Search

The main reason of text fields existence is to be used in search. Nixiesearch has two types of indexes can be used for search, lexical and semantic:

* **lexical**: an industry traditional BM25 keyword search, like in Elastic/SOLR before 2022. Nowadays called as `sparse retrieval`.
* **semantic**: an a-kNN vector-based search over embeddings of documents. A.k.a `dense retrieval`.

By default all text fields are not searchable, and you need to explicitly enable either lexical, or semantic retrieval, or both at the same time:

```yaml
schema:
  movies:
    fields:
      title:
        type: text
        search:
          semantic: # build an embedding HNSW index 
            model: e5-small
          lexical:  # build a lexical BM25 index
            analyze: english
```

After that you can search over text fields with all [Query DSL operators](../../search/query/overview.md) Nixiesearch supports, for example `match`, `semantic` and `rrf`:

```shell
curl -XPOST http://localhost:8080/v1/index/movies/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "rrf": {
        "queries": [
          {"match": {"title": "batman"}},
          {"semantic": {"title": "batman nolan"}}
        ],
        "rank_window_size": 20
      }
    },
    "fields": ["title"],
    "size": 5
  }'
```

#### Multi-vector search for text[] fields

For `text[]` fields with semantic search enabled, Nixiesearch uses a multi-vector approach where each embedding is indexed separately. During indexing, child documents are created using Lucene's block-join structure, with one child per embedding.

**Note**: For pre-embedded documents, the number of embeddings can differ from the number of text values. Specifically, a single text value can have multiple embeddings (useful for multi-perspective or chunk-based representations), but each text value must have at least one embedding in a 1:1 or 1:N relationship. See [pre-embedded text fields](../format.md#pre-embedded-text-fields) for ingestion format details.

At search time, the [`knn`](../../search/query/retrieve/knn.md) and [`semantic`](../../search/query/retrieve/semantic.md) queries use Lucene's `DiversifyingChildrenFloatKnnVectorQuery` to:
1. Find the k-nearest child documents (embeddings) across all items in the field
2. Aggregate children back to their parent documents
3. Use the maximum similarity score among all children as the document score

This ensures the most relevant embedding drives the document's relevance, useful for fields like product descriptions, document chunks, or review collections.

### Facets, filters and sorting

See [facets](../../search/facet.md), [filters](../../search/filter.md) and [sorting](../../search/sort.md) sections for more details.

### Suggestions

Text fields can also be used for creating autocomplete suggestions:

```shell
curl -XPOST -d '{"query": "h", "fields":["title"]}' http://localhost:8080/v1/index/<index-name>/suggest

```

The request above emits the following response:

```json
{
  "suggestions": [
    {"text": "hugo", "score": 2.0},
    {"text": "hugo boss", "score": 1.0},
    {"text": "hugo boss red", "score": 1.0}
  ],
  "took": 11
}
```

See [Autocomplete suggestions](../../autocomplete/index.md) section for more details.

For further reading, check out how to define [numeric](numeric.md) fields in the index mapping.

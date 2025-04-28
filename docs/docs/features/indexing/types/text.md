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

## HNSW index parameters

When a text field has a semantic search enabled, there are a couple of parameters you can further configure:

```yaml
schema:
  movies:
    fields:
      title:
        type: text      # only a single title is allowed
        search:
          semantic:
            model: e5-small
            ef: 32
            m: 16
            quantize: float32
            workers: 4
```

Fields:

* `ef` and `m`: HNSW index parameters. The higher these values, the better the search recall at the cost of performance.
* `quantize` (optional, `float32`/`int8`/`int4`, default `float32`): index quantization level. `int8` saves 4x RAM and disk but at the cost of worse recall.
* `workers` (optional, int, default is same as number of CPUs in the system): how many background workers to use for HNSW indexing operations.

## Operations on text fields

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

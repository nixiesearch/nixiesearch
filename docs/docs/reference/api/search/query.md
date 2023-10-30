# Query DSL

Nixiesearch has a Lucene-inspired query DSL with multiple search operators.

> To search over a field, make sure that this field is marked as searchable in [index mapping](../../config/mapping.md).

## Search operators

Currently three search operators are supported:

* [match](#match): search over a single field
* [multi_match](#multimatch): search over multiple fields
* [match_all](#matchall): match all documents

> All search operators can be combined with [filters](filter.md) to search over a subset of documents.

### match

Match query can be written in two JSON formats. A full version:

```json
{
  "query": {
    "match": {
      "<field-name>": {
        "query": "<search-query>",
        "operator": "and"
      }
    }
  }
}
```
Or a shorter version:

```json
{
  "query": {
    "match": {
      "<field-name>": "<search-query>"
    }
  }
}
```

Where:

* `<field-name>`: is an existing field [marked as searchable](../../config/mapping.md).
* `<search-query>`: a search query string.
* `operator`: optional, possible values: `"and"`, `"or"`. For lexical search, should documents contain all or some of terms from the search query. For semantic search this parameter is ignored.

### multi_match

An operator similar to [match](#match) but able to search multiple fields at once:

```json
{
  "query": {
    "multi_match": {
      "fields": ["<field-name>", "<field-name>"],
      "query": "<search-query>",
      "operator": "and"
    }
  }
}
```

Where:

* `<field-name>`: is an existing field [marked as searchable](../../config/mapping.md).
* `<search-query>`: a search query string.
* `operator`: optional, possible values: `"and"`, `"or"`. For lexical search, should documents contain all or some of terms from the search query. For semantic search this parameter is ignored.

Compared to Lucene-based search engines, Nixiesearch does a [RRF](#rrf-reciprocal-rank-fusion) mixing of documents matched over different fields:

1. At first pass, documents matching each separate field are collected.
2. At next step N separate per-field search results are merged together into a single ranking.

This approach allows mixing search results made over multiple fields of different underlying search types, e.g. combining lexical and semantic search over N fields.

### match_all

A search operator matching all documents in an index. Useful when combining with [filters](filter.md) to search over a subset of documents.

```json
{
  "query": {
    "match_all": {}
  }
}
```

`match_all` operator has no parameters.

## RRF: Reciprocal Rank Fusion

When you search over multiple fields marked as [semantic](../../config/mapping.md) and [lexical](../../config/mapping.md), or over a [hybrid](../../config/mapping.md) field, Nixiesearch dows the following:

1. Collects a separate per-field search result list.
2. Merges N search results with RRF - [Reciprocal Rank Fusion](#TODO).

![RRF](../../../img/hybridsearch.png)

RRF merging approach:

* Does not use a document score directly (so BM25 or cosine-distance), but a document position in a result list when sorted by the score.
* Scores of documents from multiple lists are combined together.
* Final ranking is made by sorting merged document list by the combined score.

Compared to traditional methods of combining multiple BM25 and cosine scores together, RRF does not depend on the scale and statistical distribution of the underlying scores - and can generate more stable results.
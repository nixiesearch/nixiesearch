# Query DSL

Nixiesearch has a Lucene-inspired query DSL with multiple search operators.

> To search over a field, make sure that this field is marked as searchable in [index mapping](../../features/indexing/mapping.md).

Unlike Elastic/OpenSearch query DSL, Nixiesearch has a distinction between search operators and filters:

* Search operators affect document relevance scores (like [semantic](semantic.md) and [match](match.md))
* Filters only include/exclude documents. See [Filters](../filter.md) for more details. 

## Search operators

Nixiesearch supports following search operators:

* [match](#match): search over a single field
* [multi_match](#multi_match): search over multiple fields
* [match_all](#match_all): match all documents

> All search operators can be combined with [filters](filter.md) to search over a subset of documents.

### match

Match query can be written in two JSON formats. A full version:

```json
{
  "query": {
    "match": {
      "<field-name>": {
        "query": "<search-query>",
        "operator": "or",
        "threshold": 0.666
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

* `<field-name>`: is an existing field [marked as searchable](../../features/indexing/mapping.md).
* `<search-query>`: a search query string.
* `operator`: optional, possible values: `"and"`, `"or"`. Default is "or". For lexical search, should documents contain all or some of the terms from the search query. For semantic search this parameter is ignored.
* `threshold`: optional, a cosine similarity threshold

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

* `<field-name>`: is an existing field [marked as searchable](../../features/indexing/mapping.md).
* `<search-query>`: a search query string.
* `operator`: optional, possible values: `"and"`, `"or"`. For lexical search, should documents contain all or some of terms from the search query. For semantic search this parameter is ignored.

Compared to Lucene-based search engines, Nixiesearch does a [RRF](../../features/search/overview.md#hybrid-search-with-reciprocal-rank-fusion) mixing of documents matched over different fields:

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

## Wildcard field queries

To allow more dynamism in index schema, you can use `*` wildcard placeholder in field names:

```yaml
schema:
  movies:
    extra_*:
      type: text
      search:
        type: lexical
        language: en
```

So all fields matching the wildcard pattern are going to be treated according to the schema. Wildcard fields have minor limitations:

* only a single `*` placeholder is allowed.
* you cannot have a non-wildcard field defined matching a wildcard pattern (e.g. having both a regular `title_string` field and a wildcard `*_string` in the same index).

To search over a wildcard field, you have to use the exact field name:

```json
{
  "query": {
    "match": {
      "extra_title": "<search-query>"
    }
  }
}
```

Although it's possible to use wildcard placeholders in retrieval fields:

```json
{
  "query": {
    "match_all": {}
  },
  "fields": ["extra_*"]
}
```

Which will return all fields stored for a document matching the `extra_*` wildcard pattern.
# Search request format

Search request format is similar to existing Lucene-based search engines:

```json
{
  "query": {
    "match_all": {}
  },
  "fields": ["title", "desc"],
  "size": 10,
  "aggs": {
    "color_counts": {"term": {"field": "color"}}
  },
  "filters": {
    "include": {"term": {"field": "category", "value": "pants"}}
  }
}
```

Where fields are:

* `query`: required, a search query operator. See [Query DSL](query.md) for all supported values.
* `fields`: optional (default: all stored fields), which document fields to return in the response payload. Note that these fields should be marked as `store: true` in [index mapping](../../config/mapping.md).
* `size`: optional (default: 10), number of documents to return
* `aggs`: optional, facet aggregations, see [Facets](facet.md) for more examples.
* `filters`: optional, include/exclude [filters](filter.md) to select a sub-set of documents for searching.
# Search

To search for documents indexed in Nixiesearch, you can use the following [request JSON format](../reference/api/search/request.md):

```json
{
  "query": {
    "match": {
      "<search-field-name>": "<query-string>"
    }
  }
}
```

Where:

* `<search-field-name>`: a text field marked as [searchable in the index mapping](../reference/config/mapping.md)
* `<query-string>`: a string to search for.

Check more examples of [Query DSL](../reference/api/search/query.md) in the reference.

For such a search request, Nixiesearch will reply with a JSON response with top-N matching documents:

```json
{
  "took": 100,
  "hits": [
    {"_id": "1", "title": "hello", "_score": 2},
    {"_id": "2", "title": "world", "_score": 1}
  ]
}
```

`_id` and `_score` are built-in fields always present in the payload. 

> Compared to Elasticsearch/Opensearch, Nixiesearch has no built-in `_source` field as it is frequently mis-used. You need to explicitly mark fields you want to be present in response payload as `store: true` in the [index mapping](../reference/config/mapping.md).

## Filters

To select a sub-set of documents for search, add `filters` directive to the [request JSON payload](../reference/api/search/request.md):

```json
{
  "query": {
    "match_all": {}
  },
  "filters": {
    "include": {
      "term": {
        "field": "color",
        "value": "red"
      }
    }
  }
}
```
Nixiesearch supports the following set of filter types:

* [Term filters](../reference/api/search/filter.md#term-filters) - to match over text fields.
* [Range filters](../reference/api/search/filter.md#range-filters) - to select over numeric `int`/`long`/`float`/`double` fields.
* [Compound boolean filters](../reference/api/search/filter.md#boolean-filters) - to combine multiple filter types within a single filter predicate.

See [Filters DSL](../reference/api/search/filter.md) reference for more examples and details. 

## Facets

Facet count aggregation is useful for building a [faceted search](https://en.wikipedia.org/wiki/Faceted_search): for a search query apart from documents, response contains also a set of possible filter values (sorted by a number of documents this filter value will match).

A [JSON search request](../reference/api/search/request.md) payload can be extended with the `aggs` parameter:

```json
{
  "query": {
    "multi_match": {}
  },
  "aggs": {
    "count_colors": {
      "term": {
        "field": "color",
        "count": 10
      }
    }
  }
}
```

Where `count_colors` is an aggregation name, this is a `term` aggregation over a field `color`, returning top-`10` most frequent values for this field.

Each facet aggregation adds an extra named section in the search response payload:

```json
{
  "hits": [
    {"_id": "1", "_score": 10},
    {"_id": "1", "_score": 5},
  ],
  "aggs": {
    "count_colors": {
      "buckets": [
        {"term": "red", "count": 10},
        {"term": "green", "count": 5},
        {"term": "blue", "count": 2},
      ]
    }
  }
}
```

See a [Facet Aggregation DSL](../reference/api/search/facet.md) section in reference for more details.
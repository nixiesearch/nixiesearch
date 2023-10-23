# Facet aggregations

Facet aggregation counters can help in building a filter-based faceted search, when for a single search query you get not only search results, but also all possible filter values, sorted by the number of matching documents:

```json
{
  "query": {"match": {"title": "socks"}},
  "aggs": {
    "colors": {"term": {"field": "color"}}
  }
}
```

will result in the following response:

```json
{
  "hits": ["<doc1>", "<doc2>", "..."],
  "aggs": {
    "colors": {
      "buckets": {
        "red": 10,
        "green": 5,
        "blue": 1
      }
    }
  }
}
```

Where the field `aggs.colors.buckets` contains possible filterable field values, sorted by the number of documents matching such a filter. The JSON schema for the aggregation field is:

```json
{
  "aggs": {
    "<aggregation_name_1>": {
      "<aggregation_type": { "...": "..." }
    },
    "<aggregation_name_2>": {
      "<aggregation_type": { "...": "..." }
    }
  }
}
```

> Single request can contain multiple aggregations as long as they have unique names.

Nixiesearch supports the following types of facet aggregations:
* [Term](#term-aggregations) facet counters with a number of documents matching each distinct filter value.
* [Range](#range-aggregations) counters to number the amount of documents within each defined range.

## Term aggregations

## Range aggregations
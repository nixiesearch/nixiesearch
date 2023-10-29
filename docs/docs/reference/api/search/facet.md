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

A sample query with an aggregation over a `color` field above, will result in the following response with all the possible colors in matching documents:

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

The JSON schema for the aggregation field is:

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

Nixiesearch currently supports the following types of facet aggregations:

* [Term](#term-aggregations) facet counters with a number of documents matching each distinct filter value.
* [Range](#range-aggregations) counters to number the amount of documents within each defined range.

## Term aggregations

A term facet aggregation scans over all values of a specific field of matching documents, and builds a list of top-N values:

```json
{
  "aggs": {
    "count_colors": {
      "term": {
        "field": "color",
        "size": 10
      }
    }
  }
}
```

Term aggregation has the following parameters:

* `field`: ***required***, *string*, over which field to aggregate over. The field must be marked as `facet: true` in the [index mapping](../../config/mapping.md).
* `size`: ***optional***, *integer* or `"all"`, how many top values to collect, default: *10*. A special `"all"` value is a substitute for `Integer.MAX_VALUE` - useful when you need to receive all values.

Term aggregation response has a list of N buckets and counters, sorted from most to least popular:

```json
{
  "hits": ["<doc1>", "<doc2>", "..."],
  "aggs": {
    "count_colors": {
      "buckets": {
        "red": 10,
        "green": 5,
        "blue": 1
      }
    }
  }
}
```

> Computing term facet aggregations requires creating an internal [Lucene DocValues](https://lucene.apache.org/core/9_0_0/core/org/apache/lucene/index/DocValues.html) field, which has to be kept in RAM for the best performance. Try to minimize the amount of faceted fields to keep RAM usage low.

## Range aggregations

Range aggregation scans over all values of a specific numerical field for matching documents, and builds a list of top-N ranges:

```json
{
  "aggs": {
    "count_prices": {
      "range": {
        "field": "price",
        "ranges": [
          {"lt": 10},
          {"gte": 10, "lt": 100},
          {"gte": 100}
        ]
      }
    }
  }
}
```

Range facet aggregation has the following parameters:

* `field`: ***required***, *string*. A field to compute range aggregation. Should be marked as `facet: true` in [index mapping](../../config/mapping.md) and had the type of `int`/`float`/`double`/`long`
* `ranges`, ***required***, non-empty list.
* `ranges.lt`, ***optional***, *number*. **L**ess **T**han. An end of the range, not inclusive.
* `ranges.lte`, ***optional***, *number*. **L**ess **T**han or **E**quals. An end of the range, inclusive.
* `ranges.gt`, ***optional***, *number*. **G**reater **T**han. A start of the range, not inclusive.
* `ranges.gte`, ***optional***, *number*. **G**reater **T**han or **E**quals. A start of the range, inclusive.

>A single range must have at least one `gt`/`gte`/`lt`/`lte` field.

Range facet aggregation response keeps the same ranges as in request, but adds a `count` field to each of them:

```json
{
  "hits": ["<doc1>", "<doc2>", "..."],
  "aggs": {
    "count_prices": {
      "buckets": [
        {"lt": 10, "count": 10},
        {"gte": 10, "lt": 100, "count": 4},
        {"gte": 5, "count": 2}
      ]
    }
  }
}

```

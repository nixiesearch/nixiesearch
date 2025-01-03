# Filters

Filters allow you to search over a subset of documents based on a set of predicates. Compared to traditional Lucene-based search engines, Nixiesearch filters are defined separately from the text query and do not affect ranking:

```json
{
  "query": { "match": { "title": "socks"}},
  "filters": {
    "include": {
      "term": { "size": "XXL" }
    },
    "exclude": {
      "range": { "price": {"gte": 100}}
    }
  }
}
```

`filters` can `include` and `exclude` documents based on multiple types of filters:

* [`term`](#term-filters) for text predicates: match only documents where `color=red`
* [`range`](#range-filters) for numerical ranges: match documents with `price>100`
* `and`/`or`/`not` for combining multiple filters into a single boolean expression.

> To perform filtered queries over a field, you should define the field as `filter: true` in [index mapping](../../features/indexing/mapping.md).
> Nixiesearch will emit a warning if you relentlessly try to filter over a non-filterable field.

## Term filters

Term predicate can be defined as a simple JSON key-value pair, where key is a field name, and value is a predicate:

```json
{
  "query": { "match_all": {}},
  "filters": {
    "include": {
      "term": {
        "<field_name>": "<field_value>"
      }
    }
  }
}
```
!!! note 

    A simple term filter works only with a single field and a single value. If you want to filter over multiple fields and multiple values, use a [boolean filter](#boolean-filters) to combine them together in a single expression.
 
Term filters currently support the following field types: `int`, `long`, `string`, `boolean`. For example, filtering over a boolean field called `active` can be done with the following query:

```json
{
  "query": { "match_all": {}},
  "filters": {
    "include": {
      "term": {
        "active": true
      }
    }
  }
}
```

## Range filters

Range filters allow defining open and closed ranges for numeric fields of types [int, long, double, float] to pre-select documents for search:

```json
{
  "query": { "match_all": {}},
  "filters": {
    "include": {
      "range": {
        "<field_name>": { "gte": 100.0, "lte": 1000.0 }
      }
    }
  }
}
```

Range filter takes following arguments:

* `<field_name>` a numeric field marked as `filter: true` in the index mapping
* `gt`/`gte`: Greater Than (or Equals), optional field
* `lt`/`lte`: Less Than (or Equals), optional field. 

There must be at least one `gt`/`gte`/`lt`/`lte` field present in the filter.

## Boolean filters

You can combine multiple basic range and term filters together into a more complicated boolean expression using `and`, `or` and `not` filter types from the boolean family. Each of these filter types takes a list of other filters as an argument:

```json
{
  "query": {"match_all": {}},
  "filters": {
    "include": {
      "and": [ "<filter 1>", "<filter 2>", "..." ]
    }
  }
}
```

For example, to match documents with multiple field values at once, you can define the following query:

```json
{
  "query": { "match_all": {}},
  "filters": {
    "include": {
      "or": [
        { "term": { "color": "red" }},
        { "term": { "color": "green"}}
      ]
    }
  }
}
```

Nesting of boolean filters is also possible:

```json
{
  "query": { "match_all": {}},
  "filters": {
    "include": {
      "and": [
        {"range": { "price": {"gte": 100}}},
        {
          "or": [
            {"term": {"color": "red"}},
            {"term": {"color": "green"}}
          ]
        }
      ]
    }
  }
}
```

## Geolocation filters

With a `geopoint` datatype, you can filter by a `geo_distance` and `geo_box` predicates.

### Distance filters

With a distance query, you can include/exclude documents being within a distance from a point. Query example:

```json
{
  "query": { "match_all": {}},
  "filters": {
    "include": {
      "geo_distance": {
        "field": "<field_name>",
        "lat": 1.0,
        "lon": 2.0,
        "distance": "1 km"
      }
    }
  }
}
```

Following distance units are supported: 

* Millimeters: `mm`, `millimeters`, `millimeter`
* Centimeters: `cm`, `centimeters`, `centimeter`
* Meters: `m`, `meter`, `meters`
* Kilometers: `km`, `kilometer`, `kilometers`
* Inches: `in`, `inch`, `inches`
* Feet: `ft`, `foot`, `feet`
* Yards: `yd`, `yard`, `yards`
* Miles: `mi`, `mile`, `miles`

### Bounding box filters

With a bounding box query, you can include documents laying within a specific rectangle.

```json
{
  "query": { "match_all": {}},
  "filters": {
    "include": {
      "geo_box": {
        "field": "<field_name>",
        "top_left": {"lat": 1.0, "lon": 2.0},
        "bottom_right": {"lat": 3.0, "lon": 4.0}
      }
    }
  }
}
```


## Filters and lexical/semantic search

Nixiesearch relies on Lucene logic to handle filter execution:

* for **lexical** search include/exclude filters are fused together into a single Lucene query, doing filtering and ranking in a single pass.
* for **semantic** search filter behavior is selected at run-time based on filter coverage estimation. 

Narrow filters (e.g. selecting only small amount of documents) are defined as pre-filters and executed before the query. Wide filters (e.g. selecting a lot of documents) are executed as post-filters after the main search query. This adaptive behavior is made for performance reasons.
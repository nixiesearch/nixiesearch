# multi_match

A `multi_match` query performs a **lexical** search over multiple  searchable **text** fields. The search query is analyzed before performing search.

If you would like to search over a semantic-indexed field, consider a [`semantic`](semantic.md) and [`knn`](knn.md) search operators instead.

An operator similar to [match](match.md) but able to search multiple fields at once:

```json
{
  "query": {
    "multi_match": {
      "fields": ["<field-name>", "<field-name>"],
      "query": "<search-query>",
      "type": "best_fields" //(1) 
    }
  }
}
```

1. optional

Where:

* `<field-name>`: is an existing field [marked as searchable](../../../indexing/mapping.md).
* `<search-query>`: a search query string.
* `operator`: optional, possible values: `"best_fields"`, `"most_fields"`, default `"best_fields"`. The way field scores are combined when multiple of them are matched at once. 

## Wildcard fields

`multi_match` query also supports [wildcard fields](../../../indexing/mapping.md#wildcard-fields), so you can make requests like this example:


```json
{
  "query": {
    "multi_match": {
      "fields": ["desc_*"],
      "query": "cookies" 
    }
  }
}
```

This query will perform a `multi_match` over all fields starting with `desc_` prefix.

## Types of `multi_match` queries

The `multi_match` query has multiple behaviors depending on the `type` parameter:

1. `best_fields` (default): Finds all documents which match any field, but only the `_score` from the best matching field is used.
2. `sdf`

### best_fields

An example for `best_fields` query is searching over `title` and `description` fields, where a match over a `title` field might be more important than match over the `desription` field.

The full schema of the query:

```json
{
  "query": {
    "multi_match": {
      "fields": ["title", "description"],
      "query": "cookies",
      "type": "best_fields",
      "tie_breaker": 0.3
    }
  }
}
```

The `best_fields` query is an alias for a [`dis_max`](dis_max.md) query, which generates multiple per-field `match` queries and wraps them the following way:

```json
{
  "query": {
    "dis_max": {
      "queries": [
        {"match": {"title": "cookies"}},
        {"match": {"description": "cookies"}}
      ],
      "tie_breaker": 0.3
    }
  }
}
```

Normally the `best_fields` type uses the score of the single best matching field, but if tie_breaker is specified, then it calculates the score as follows:

1. the score from the best matching field
2. plus `tie_breaker * _score` for all other matching fields

### most_fields

The `most_fields` query type is useful when searching over similar fields analyzed in a different manner, for example in different languages:

```yaml
schema:
  my-index:
    fields:
      title_english:
        type: text
        search:
          lexical:
            analyze: english
      title_spanish:
        type: text
        search:
          lexical:
            analyze: spanish
```

So the following query can be used:

```json
{
  "query": {
    "multi_match": {
      "fields": ["title_english", "title_spanish"],
      "query": "cookies",
      "type": "most_fields"
    }
  }
}
```

Internally the `most_fields` query is implemented as a [`bool`](bool.md) query over multiple per-field [`match`](match.md) queries:

```json
{
  "query": {
    "bool": {
      "should": [
        {"match": {"title_english": "cookies"}},
        {"match": {"title_spanish": "cookies"}}
      ]
    }
  }
}
```


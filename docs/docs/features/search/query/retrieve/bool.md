# bool query

The `bool` query can be used to combine multiple child queries into a single search expression. The request schema is:

```json
{
  "query": {
    "bool": {
      "should": ["list of sub-queries"],
      "must": ["list of sub-queries"],
      "must_not": ["list of sub-queries"]
    }
  }
}
```

* `should` queries are the best effort ones, but at least once of them must match the documents. The more sub-queries match, the higher the final document `_score` is.
* `must` queries are required, so all of them have to match for a document to be included in results.
* `must_not` queries are required NOT to match. All of them should NOT match for a document to be matched.

The [`multi_match`](multi_match.md) query for `most_fields` is implemented as a `bool` query.

Example:

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
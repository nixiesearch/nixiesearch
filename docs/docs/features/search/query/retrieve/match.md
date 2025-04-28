# match

A match query performs a **lexical** search over a searchable **text** field. The search query is analyzed before performing search.

If you would like to search over a semantic-indexed field, consider a [`semantic`](semantic.md) and [`knn`](knn.md) search operators instead.

Match query can be written in two JSON formats. A full version:

```json
{
  "query": {
    "match": {
      "<field-name>": {
        "query": "<search-query>",
        "operator": "or"
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

* `<field-name>`: is an existing field [marked as searchable](../../../indexing/mapping.md) with lexical search support enabled.
* `<search-query>`: a search query string.
* `operator`: optional, possible values: `"and"`, `"or"`. Default is "or". For lexical search, should documents contain all or some of the terms from the search query. For semantic search this parameter is ignored.

# knn query

A `knn` search query can be used for searching over [`text`](../../../indexing/types/text.md) fields defined as searchable with semantic search with a pre-computed embedding.

Unlike [`semantic`](semantic.md) query, the `knn` query does NOT run embedding inference, and expects the query embedding provided in the request:

```json
{
  "query": {
    "knn": {
      "field": "title",
      "query_vector": [1,2,3,4,5],
      "k": 10
    }
  }
}
```

Fields:
* `field`: a [`text`](../../../indexing/types/text.md) field name with semantic search enabled in the [index mapping](../../../indexing/mapping.md).
* `query_embedding`: a text query embedding.
* `k`: an optional parameter of how many neighbor documents to fetch. By default, equals to the `request.size` field.

For a case when you would like Nixiesearch to embed the query, see the [`semantic`](semantic.md) query.
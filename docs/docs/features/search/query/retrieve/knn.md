# knn query

A `knn` search query can be used for searching over [`text`](../../../indexing/types/text.md) fields defined as searchable with semantic search with a pre-computed embedding.

**Note**: The `knn` query works with both server-side inference (using the `model` parameter) and pre-embedded documents (using the `dim` parameter). For text-based queries that require server-side embedding computation, use the [`semantic`](semantic.md) query instead.

Unlike [`semantic`](semantic.md) query, the `knn` query does NOT run embedding inference, and expects the query embedding provided in the request:

```json
{
  "query": {
    "knn": {
      "field": "title",
      "query_vector": [1,2,3,4,5],
      "k": 10,
      "num_candidates": 15
    }
  }
}
```

Fields:
* `field`: a [`text` or `text[]`](../../../indexing/types/text.md) field name with semantic search enabled in the [index mapping](../../../indexing/mapping.md).
* `query_vector`: a text query embedding.
* `k`: an optional parameter of how many neighbor documents to fetch. By default, equals to the `request.size` field.
* `num_candidates`: an optional parameter for the number of nearest neighbor candidates to consider per shard while doing knn search. Cannot exceed 10,000. Increasing num_candidates tends to improve the accuracy of the final results. Defaults to 1.5 * k if k is set, or 1.5 * size if k is not set.

**Note**: For `text[]` fields, the query uses multi-vector search where the highest-scoring item in the array determines the document score. See [text field types](../../../indexing/types/text.md#multi-vector-search-for-text-fields) for details.

For a case when you would like Nixiesearch to embed the query, see the [`semantic`](semantic.md) query.
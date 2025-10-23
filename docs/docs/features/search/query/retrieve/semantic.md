# semantic query

A `semantic` query can be used to search [`text`](../../../indexing/types/text.md) fields with semantic search enabled. Unlike the `knn` query, the `semantic` query accepts text query string and computes embeddings. 

**Note**: The `semantic` query only works with fields that have server-side inference configured (i.e., using the `model` parameter). For pre-embedded documents (using the `dim` parameter), use the [`knn`](knn.md) query instead.

So for a field `title` defined as:

```yaml
inference:
  embedding:
    e5-small:
      model: intfloat/e5-small-v2 # perform local ONNX inference
schema:
  my-index:
    fields:
      title:
        type: text
        search:
          semantic:           # build an a-kNN HNSW index
            model: e5-small   # use this model for embeddings
```

Such a field can be searched with the `semantic` query:

```json
{
  "query": {
    "semantic": {
      "field": "title",
      "query": "cookies",
      "k": 20,
      "num_candidates": 30
    }
  }
}
```

Or with a shorter form:

```json
{
  "query": {
    "semantic": {"title": "cookies"}
  }
}
```

Where the fields are:

* `field`: a [`text` or `text[]`](../../../indexing/types/text.md) field with semantic search enabled in the [index mapping](../../../indexing/mapping.md).
* `query`: a text query. The query is not analyzed and fed into the embedding model as-is.
* `k`: an optional parameter of how many neighbor documents to fetch. By default, equals to the `request.size` field.
* `num_candidates`: an optional parameter for the number of nearest neighbor candidates to consider per shard while doing knn search. Cannot exceed 10,000. Increasing num_candidates tends to improve the accuracy of the final results. Defaults to 1.5 * k if k is set, or 1.5 * size if k is not set.

**Note**: For `text[]` fields, the query uses multi-vector search where the highest-scoring item in the array determines the document score. See [text field types](../../../indexing/types/text.md#multi-vector-search-for-text-fields) for details.

For a case when you already have a pre-embedded query and want to search over the embedding vector directly skipping the inference, see the [`knn`](knn.md) query.

## Improving Semantic Search Relevance

For improved relevance scoring beyond vector similarity, consider using [cross-encoder reranking](../rank/ce.md) to rerank semantic search results with neural models that jointly process query-document pairs.
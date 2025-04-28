# semantic query

A `semantic` query can be used to search [`text`](../../../indexing/types/text.md) fields with semantic search enabled. Unlike the `knn` query, the `semantic` query accepts text query string and computes embeddings. So for a field `title` defined as:

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
      "k": 20 
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

* `field`: a [`text`](../../../indexing/types/text.md) field with semantic search enabled in the [index mapping](../../../indexing/mapping.md).
* `query`: a text query. The query is not analyzed and fed into the embedding model as-is.
* `k`: an optional parameter of how many neighbor documents to fetch. By default, equals to the `request.size` field.

For a case when you already have a pre-embedded query and want to search over the embedding vector directly skipping the inference, see the [`knn`](knn.md) query.
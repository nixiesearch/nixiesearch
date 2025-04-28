# Query DSL

Nixiesearch has a Lucene-inspired query DSL with multiple search operators.

!!! note

    To search over a field, make sure that this field is marked as searchable in [index mapping](../../features/indexing/mapping.md).

Unlike Elastic/OpenSearch query DSL, Nixiesearch has a distinction between search operators and filters:

* Search operators affect document relevance scores (like [semantic](semantic.md) and [match](match.md))
* Filters only control how we include/exclude documents. See [Filters](../filter.md) for more details. 

## Search operators

Search operators allow you to actually perform the full-text search over your documents. They're designed to be fast and quickly get top-N most relevant document for your search query.

Nixiesearch supports the following search operators:

* [match](retrieve/match.md): search over a single field
* [multi_match](retrieve/multi_match.md): search over multiple fields at once.
* [match_all](retrieve/match_all.md): match all documents.
* [semantic](retrieve/semantic.md): embed a query and perform a-kNN vector search over document embeddings.
* [knn](retrieve/knn.md): perform a-kNN vector search over document embeddings (without embedding the query).

Operators can be combined into a single query:

* [dis_max](retrieve/dis_max.md): search over multiple fields, but sort by the most matching field score.
* [bool](retrieve/bool.md): combine multiple queries in a boolean expression.

!!! note 

    All search operators can be combined with [filters](filter.md) to search over a subset of documents.

## Ranking operators

Rank operators accept one or more [search operators](#search-operators) but only operate on top-N of them.

Nixiesearch supports the following list of rank operators:

* [RRF](rank/rrf.md): Reciprocal Rank Fusion, merge two search results lists based on document position.




### match_all

A search operator matching all documents in an index. Useful when combining with [filters](filter.md) to search over a subset of documents.

```json
{
  "query": {
    "match_all": {}
  }
}
```

`match_all` operator has no parameters.

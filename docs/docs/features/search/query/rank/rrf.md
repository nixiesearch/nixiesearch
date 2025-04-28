# RRF ranker

[Reciprocal Rank Fusion (RRF)](https://plg.uwaterloo.ca/%7Egvcormac/cormacksigir09-rrf.pdf) is a simple method to combine multiple search results with different search score numerical distributions into a single list.

The main benefit of RRF is that it's lightweight and requires no tuning, but might provide less relevant results compared to other more computationally intensive ranking methods like [Learn-to-Rank](https://metarank.ai) and Cross-Encoders.

The `rrf` rank operator takes two or more child sub-queries:

```json
{
  "query": {
    "rrf": {
      "queries": [
        {"match": {"title": "cookie"}},
        {"semantic": {"title": "cookie"}}
      ],
      "k": 60.0,
      "rank_window_size": 20
    }
  }
}
```

And combines their score in the following way ([taken from Elasticsearch docs](https://www.elastic.co/docs/reference/elasticsearch/rest-apis/reciprocal-rank-fusion)):

```python
score = 0.0
for q in queries:
    if d in result(q):
        score += 1.0 / ( k + rank( result(q), d ) )
return score

# where
# k is a ranking constant
# q is a query in the set of queries
# d is a document in the result set of q
# result(q) is the result set of q
# rank( result(q), d ) is d's rank within the result(q) starting from 1
```

Fields:

* `queries` (required, list of [search queries](../overview.md#search-operators)). Two or more nested search queries to combine.
* `k` (optional, float, default 60.0). The ranking constant - how strongly lower document position affects the score.
* `rank_window_size` (optional, integer, default is `request.size`) This value determines the size of the individual result sets per query. A higher value will improve result relevance at the cost of performance. The final ranked result set is pruned down to the search request’s size. 

The RRF ranker supports [filters](../../filter.md) and [aggregations](../../facet.md), but does not support [sorting](../../sort.md).
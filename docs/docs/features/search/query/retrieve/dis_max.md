# Disjunction-max query

Returns documents matching one or more wrapped queries.

If a returned document matches multiple query clauses, the `dis_max` query assigns the document the highest relevance score from any matching clause, plus a tie breaking increment for any additional matching subqueries.

## Example request

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

Fields:

* `queries`: required, a list of queries. Returned documents must match one or more of these sub-queries. If the doc matches multiple sub-queries, then the highest score is used.
* `tie_breaker`: optional, float. A number between `0.0` and `1.0` used to increase scores of docs matching multiple queries at once.

## The tie breaker

The `tie_breaker` parameter allows you to prioritize documents where the same keyword appears across several fields, making them score higher than documents where the keyword is present in only the most relevant single field. This distinction ensures that documents with multiple field matches for the same term are not confused with documents matching different terms across fields.

When a document satisfies more than one condition, the `dis_max` query computes its relevance score using the following steps:

1. Identify the clause that produced the highest individual score.

2. Apply the `tie_breaker` coefficient to the scores of the other matching clauses.

3. Add the highest individual score to the adjusted scores of the other clauses.

When the `tie_breaker` is set above `0.0`, all matching clauses contribute to the final score, but the clause with the highest individual score remains the most influential.
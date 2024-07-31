# Autocomplete suggestions API 

## Suggestion index configuration

You need to explicitly define a *suggest* flag for a field in [index mapping](todo) to be able to query for suggestions. Nixie supports two flavors of syntax definitions for suggestions:

* short one: only `suggest: true` using all default settings,
* long syntax: more verbose, but all settings are exposed.

Example of a short syntax:

```yaml
my-index:
  fields:
    title:
      type: text
      search: lexical
      suggest: true
```

With longer suggestion syntax you have access to all the internal options:

```yaml
my-index:
  fields:
    title:
      type: text
      search: lexical
      suggest:
        lowercase: false # should we down-case all suggestions?
        expand:
          min-terms: 1
          max-terms: 3
```

Suggest parameters are defined as follows:

* `lowercase`: ***optional***, *should we downcase all strings?*, default `false`
* `expand`: **optional**, *list of suggestion candidate expansion settings*
* `expand.min-terms`: **optional**, *lower length of rolling window expansions*, default `1`
* `expand.max-terms`: **optional**, *higher length of rolling window expansions*, default `3`

## Sending suggestion requests

Suggest indices have a special `_suggest` endpoint you can use for autocomplete suggestion generation:

```shell
curl -XPOST -d '{"query": "hel", "fields":["title"]}' http://localhost:8080/<index-name>/_suggest
```

A full suggest request JSON format:

```json
{
  "query": "hu",
  "fields": ["title"],
  "count": 10,
  "rerank": {
    "rrf": {
      "depth": 50,
      "scale": 60
    }
  }
}
```

Where request fields are defined in the following way:

* `query`: **required**, *string*. A suggestion search query.
* `fields`: **required**, *string[]*. Fields to use for suggestion generation.
* `count`: **optional**, *int*, *default=10*. The number of top-level suggestions to generate.
* `rerank`: **optional**, *obj*, *default="rrf"*. Suggestion ranking method, see [section on RRF below](#reranking-configuration) for more details.

The request above emits the following response:

```json
{
  "suggestions": [
    {"text": "hugo", "score": 2.0},
    {"text": "hugo boss", "score": 1.0},
    {"text": "hugo boss red", "score": 1.0}
  ],
  "took": 11
}
```

## Reranking configuration

Suggestion request can include a single reranking processor step:

* Reciprocal Rank Fusion (RRF) reranking.
* (Planned in v0.2) Learn-to-Rank suggestions.

Reciprocal Rank Fusion configuration block has the following options:

* `rerank.rrf.depth`: How many suggestion candidates should be generated before reranking. Having more might affect latency, but increase precision. Optional, int, default=50.
* `rerank.rrf.scale`: An RRF constant scaling factor, importance of top vs bottom candidate position on the final ranking. Optional, int, default=60.

Suggestions are generated separately per each field, and then reduced together with [Reciprocal Rank Fusion (RRF)](https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf). A [pseudocode example from Elasticsearch docs about RRF]() describes the algorithm well:

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

Some important considerations regarding RRF ranking:

* Suggestions are fused into a single ranking in a case-insensitive way, so `hello` and `Hello` are considered equal.
* Suggestion candidates are not lowercased, Nixie preserves a case of the most highly ranked suggestion, and other variations are linked to it.
* By default we use scale constant for RRF as 60 to match [Elasticsearch](https://www.elastic.co/guide/en/elasticsearch/reference/current/rrf.html#rrf-api) implementation.
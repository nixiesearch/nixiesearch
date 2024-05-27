# Autocomplete suggestions

Nixiesearch uses an ES-like approach to suggestions: you mark a field in your [index mapping](todo) as a `suggest=true`
and it becomes possible to send a `_suggest` query requests. 

Nixie supports two syntaxes for enabling suggestions for a field: short and long. Short syntax has only `suggest: true` option and uses default suggestion settings:

```yaml
my-index:
  fields:
    title:
      type: text
      search: lexical
      suggest: true
```

Full syntax is a bit verbose, but allows specifying all the internal suggestion generation settings:

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

## Suggestion expansions

A `suggest.expand` group of options allows Nixiesearch to generate rolling window suggestions:

* for a field `hugo boss red dress` and query `hu` a default no-expansion approach will only have a single suggestion equal to the field.
* with expansions, the field will be expanded into multiple shorter sub-values like `hugo`, `boss`, `red`, `dress`, `hugo boss`, `boss red`, `red dress`, `hugo boss red`, `boss red dress`
* so for a query `hu` Nixie will generate `hugo`, `hugo boss`, `hugo boss red` suggestions.

## Generating suggestions

Each index with suggestable fields has a `_suggest` endpoint, which accepts suggest requests in the following format:

```json
{
  "query": "hu",
  "fields": ["title"],
  "count": 10
}
```

For such a request, Nixie will return a suggestion response:

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

Internally, Nixie unfolds a single suggestion request into multiple Lucene queries:
* Prefix match: for `hu` suggest `hugo`.
* Fuzzy matches with [Levenstein distance](https://en.wikipedia.org/wiki/Levenshtein_distance) of 1 and 2. For `man` suggest `men`.
* Substring match: for `scrape` suggest `skyscraper`.

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

See [suggestion API reference](../reference/api/suggest.md) for more details on how to configure RRF.

### Deduplication

Suggestion request also has an optional section for suggestions post-processing:

```json
{
  "query": "hu",
  "fields": ["title"],
  "count": 10,
  "process": {
    "deduplicate": "true"
  }
}
```

Currently, Nixie supports only a single post-processor, deduplication - but we plan to support more:

* LTR for suggestions: https://github.com/nixiesearch/nixiesearch/issues/173
* Lemmatization: https://github.com/nixiesearch/nixiesearch/issues/184

If you have more ideas, you're [welcome to our issues](https://github.com/nixiesearch/nixiesearch/issues).
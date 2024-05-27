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

Suggestion candidates are generated separately per field (and per method), and then ranked together with [Reciprocal Rank Fusion (RRF)](https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf).

See [suggestion API reference](../reference/api/suggest.md) for more details on how to configure RRF.


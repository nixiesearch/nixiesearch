# Autocomplete suggestions API 

## Suggestion index configuration

You need to explicitly define a *suggest* flag for a field to be able to query for suggestions. Nixie supports two flavors of syntax definitions for suggestions:

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
  "process": {
    "deduplicate": "true"
  }
}
```

Where request fields are defined in the following way:

* `query`: **required**, *string*. A suggestion search query.
* `fields`: **required**, *string[]*. Fields to use for suggestion generation.
* `count`: **optional**, *int*, *default=10*. The number of top-level suggestions to generate.
* `process`: **optional**, *obj*, *default=None*. Post-processing options.
* `process.deduplicate`: **optional**, *boolean*, *default=false*. Should the resulting suggestion list be deduplicated?

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

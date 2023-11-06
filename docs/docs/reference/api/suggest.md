# Autocomplete suggestions API 

## Suggestion index configuration

You need to explicitly define a *suggest* index in the configuration file, as Nixiesearch does not support dynamic mapping for suggestions.

```yaml
suggest:
  <suggester-index-name>:
    model: nixiesearch/nixie-suggest-small-v1
    transform:
      fields: ["title", "description"]
      language: "english"
      lowercase: true
      removeStopwords: true
      group: [1, 2, 3]
```

Suggest parameters are defined as follows:

* `model`: ***optional***, *model handle*, default `nixiesearch/nixie-suggest-small-v1`
* `transform`: ***optional***, *object*. A list of document transformations before indexing.
* `transform.fields`: ***required***, *string[]*. Fields from ingested documents to use as a source for suggestions.
* `transform.language`: ***optional***, *string*, *default `english`*. Which language to use for text transformations.
* `transform.lowercase`: ***optional***, *boolean*, *default `true*`. Should all text strings before indexing be lowercased.
* `transform.removeStopwords`: ***optional***, *boolean*, *default `true`*. Should all language-specific stopwords be dropped,
* `transform.group`: ***optional***, *int[]*, *default `[1, 2, 3]`*. How words are groupped for suggestions. By default, all single words, word tuples and triplets are indexed.

> The `model` can point to any Huggingface-hosted ONNX model, but we do not advise using any non-Nixiesearch models that are not explicitly trained on partial noisy inputs. See model description for [nixiesearch/nixie-suggest-small-v1](https://huggingface.co/nixiesearch/nixie-suggest-small-v1) for training details.

## Adding suggestions to the index

### Transforming existing documents for suggestions

When you want to index suggestions based on existing documents (like the ones you're also indexing for regular search), you can define your suggestion transformation in the suggestion index mapping in the following way:

* `transform.fields` should include all fields you plan to generate suggestions from. In other Lucene-based search engines this is similar to marking a field as `suggestable`.
* default set of transformation parameters performs some typical transformations: lowercasing, removing stopwords and grouping words in tuples of size 1, 2 and 3 words.

For example, for the following suggestion index configuration:

```yaml
suggest:
  foobar:
    transform:
      fields: ["title"]
```

Only text field `title` from the documents is indexed with a default set of suggestion transformations:

```shell
curl -XPUT -d '{"title":"foo bar baz", "description": "other desc"}' http://localhost:8080/foobar/_index
```

> Suggestion JSON format is the same as for [regular search documents](../api/index/document-format.md).

A default set of transformations will index the following suggestions: `["foo", "bar", "baz", "foo bar", "bar baz", "foo bar baz"]` 

### Raw suggestion without transformations

When you already have a set of pre-made suggestions for indexing (for example, based on a set of previously searched queries or product titles), Nixiesearch expects documents with a single `"suggest"` field as a source of suggestions:

```json
{"suggest": "hello"}
{"suggest": "help"}
{"suggest": "helps"}
{"suggest": "hip hop"}
```

> Suggestion JSON format is the same as for [regular search documents](../api/index/document-format.md).

Indexing can be performed with a cURL command in the same way as for regular documents indexing:

```shell
curl -XPUT -d @suggestions.json http://localhost:8080/<index-name>/_index
```

## Sending suggestion requests

Suggest indices have a special `_suggest` endpoint you can use for autocomplete suggestion generation:

```shell
curl -XPOST -d '{"text": "hel"}' http://localhost:8080/<index-name>/_suggest
```

A full suggest request JSON format:

```json
{
  "text": "<suggest-query>",
  "size": 10,
  "deduplication": {
    "threshold": 0.80
  }
}
```

Where request fields are defined in the following way:

* `text`: ***required***, *string*. A suggestion search query.
* `size`: ***optional***, *int*, *default=10*. The number of top-level suggestions to generate.
* `deduplication`: ***optional***, *`"false"|{"threshold":<int>}`*, *default=0.80*. Suggestion deduplication threshold, see the [chapter about deduplication](#suggestion-deduplication) for more details.


The request above emits the following response:

```json
{
  "suggestions": [
    {
      "text": "helps", 
      "score": 0.95, 
      "forms": [{"text": "help", "score": 0.90}] 
    },
    {
      "text": "hello",
      "score": 0.90,
      "forms": []
    },
    {
      "text": "hip hop",
      "score": 0.70,
      "forms": []
    }
  ]
}
```

### Suggestion deduplication

A frequent problem with generated autocomplete suggestions are duplicate results: different word forms and ways of writing are a common source of such problem (e.g. with/without dash word forms, etc.).

Nixiesearch supports suggestion deduplication by clustering suggestions over their cosine distance to each other. You can configure deduplication threshold on a request-level with a `deduplication` field:

```json
{
  "text": "<suggestion-query>",
  "deduplication": {
    "threshold": 0.80
  }
}
```

Deduplication can be disabled completely with a `false` value for `deduplication` field:

```json
{
  "text": "<suggestion-query>",
  "deduplication": "false"
}
```

Configuring deduplication threshold requires some testing, but from practical experience, you should consider these values as a baseline:

* **0.95** - only very close word forms are grouped
* **0.80** - default value
* **0.70** - very agressive deduplication

After the deduplication process, all the word forms are still returned in the response payload in the `forms` sub-field:

```json
{
  "suggestions": [
    {
      "text": "lipstick", 
      "score": 0.9, 
      "forms": [
        {"text": "lip-stick", "score": 0.8},
        {"text": "lip stick", "score": 0.7}
      ]
    }
  ]
}
```
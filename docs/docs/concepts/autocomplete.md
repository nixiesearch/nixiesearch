# Suggestions autocomplete

Unlike traditional Lucene-based search enignes, Nixiesearch used a novel semantic autocomplete suggestions algorithm:

![semantic suggestions](../img/suggestions.png)

Nixiesearch uses a custom-built embedding model [nixiesearch/nixie-suggest-small-v1](https://huggingface.co/nixiesearch/nixie-suggest-small-v1) based on [intfloat/e5-small-v2](https://huggingface.co/intfloat/e5-small-v2) with the following improvements:

* Can tolerate multiple types of typos: letter drops, swaps, duplications and changes. For example, cosine distance between terms `milk` and `mikl` is quite small.
* Fine-tuned on a task of recovering full term based on a noisy prefix. For example, cosine distance between `termi` and `terminator` is minimal. Prefix can also include typos.

Compared to traditional algorithmic approaches, the semantic one:

* Can use **semantics of surrounding words** while matching. Example: `mkli coffee` has too huge Levenstein distance in the first word for Lucene FuzzySuggester to handle the issue, but it's quite clear from the semantics of the phrase that it's probably a `milk coffee`
* **Not limited to prefix matching** and specific thresholds (like 1-2) of edit distances unlike other solutions: `mollk coffee` is still close to `milk coffee` even with edit distance of 3.
* Can handle **fully semantic matches** like `cappucino` being close to `milk coffee`.

## Creating a suggestions index

Nixiesearch only supports explicit suggestion index mapping, so to index suggestions, you need to define a [custom suggestions index](../reference/api/suggest.md#suggestion-index-mapping) in a configuration file:


```yaml
suggest:
  <suggester-index-name>:
    model: nixiesearch/nixie-suggest-small-v1
```

Unlike existing Lucene-based search engines, you need to explicitly ingest documents for suggestions into the autocomplete index:

* [Index raw suggestion strings as-is](#indexing-predefined-suggestion-strings). Useful when you want to only include a special vetted set of strings into the suggestion index, like historical search queries.
* [Transform your existing documents](#indexing-transformed-documents). Take existing documents, and expand their fields into suggestion strings.

### Indexing predefined suggestion strings

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


### Indexing documents

Instead of preparing suggestion strings by yourself, you can generate suggestions by indexing your regular documents. To make this, you need to mark suggestable fields in your index mapping:

```yaml
suggest:
  <suggester-index-name>:
    model: nixiesearch/nixie-suggest-small-v1
    transform:
      fields: [title, description]
```

With the mapping above you can `_index` the same set of documents as for regular search, Nixiesearch will extract `title` and `description` text fields from these documents, generate suggestions and index them.

> As suggestion index is a separate index with different format and semantics, you may need to index your documents twice: one time for regular search, and second time for autocomplete suggestions.

A more detailed list of supported transformations is available in [Suggestion API](../reference/api/suggest.md) reference section. 

## Sending suggestion requests

Suggest indices have a special `_suggest` endpoint you can use for autocomplete suggestion generation:

```shell
curl -XPOST -d '{"text": "hel"}' http://localhost:8080/<index-name>/_suggest
```

It will emit the following response:

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
    }
  ]
}
```

Suggestion API also supports deduplication of results, see [suggestion deduplication](../reference/api/suggest.md#suggestion-deduplication) reference section for details.
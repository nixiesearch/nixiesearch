# Search suggestions autocomplete 

To build search suggestions, Nixiesearch uses a novel semantic autocomplete algorithm:

* All possible suggestions are embedded using an in-house [nixie-suggest-small-v1](https://huggingface.co/nixiesearch/nixie-suggest-small-v1) LLM embedding model.
* The model is trained to tolerate typos (e.g. "milk" - "milk") and partial inputs (e.g. "termi" - "terminator").
* When you send a suggest request to a `_suggest` endpoint, a regular k-NN vector search is performed over a suggestion index for most similar suggestions.

> Unlike existing Lucene search engines, Nixiesearch suggest index is not tied to a specific *suggestable* field. Suggest index is just a special flavor of a regular semantic index, so you need to explicitly add suggestion documents there.

To create a suggestion index, you need:

* Create a static [suggestion index mapping](#suggestion-index-mapping). As for now, We do not support dynamic mapping for suggestion indices.
* [Add suggestion documents](#adding-suggestions-to-the-index) to the index. Only documents you've indexed will be returned in the response.
* Send a [search suggestion request](#sending-suggestion-requests). Generated suggestions can also be [deduplicated](#suggestion-deduplication) to group similar ones.

## Suggestion index mapping

You need to explicitly define a suggest index in the configuration file, as Nixiesearch does not support dynamic mapping for suggestions.

```yaml
suggest:
  <suggester-index-name>:
    model: nixiesearch/nixie-suggest-small-v1
```

The `model` can point to any Huggingface-hosted ONNX model, but we do not advise using any non-Nixiesearch models not explicitly trained on partial noisy inputs. See model description for [nixiesearch/nixie-suggest-small-v1](https://huggingface.co/nixiesearch/nixie-suggest-small-v1) for training details.

## Adding suggestions to the index

Nixiesearch expects documents with a single `"suggest"` field as a source of suggestions:

```json lines
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

* `text`: required, string. A suggestion search query.
* `size`: optional, int, default=10. How many top-level suggestions to generate.
* `deduplication`: optional, `"false"|{"threshold":<int>}`, default=0.80. Suggestion deduplication threshold, see the [next chapter about deduplication](#suggestion-deduplication) for more details.

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

A frequent problem with generated autocomplete suggestions are duplicate results: different word forms and ways of writing (with/without dash, etc.) are a common source of such problem.

Nixiesearch supports suggestion deduplication by clustering suggestion over their cosine distance to each other. You can configure deduplication threshold on a request-level with a `deduplication` field:

```json
{
  "text": "<suggestion-query>",
  "deduplication": {
    "threshold": 0.80
  }
}
```

Deduplication can be disabled completely with an `false` field value:

```json
{
  "text": "<suggestion-query>",
  "deduplication": "false"
}
```

Configuring deduplication threshold requires some testing, but from practical experience, you should consider these values as a baseline:

* **0.95** - only very close word forms are grouped
* **0.80** - a default value
* **0.70** - very agressive deduplication

After the deduplication process, all the word forms are still returned in the response payload under the `forms` sub-field:

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
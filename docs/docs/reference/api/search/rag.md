# RAG: Retrieval Augmented Generation

Nixiesearch supports [RAG](https://en.wikipedia.org/wiki/Retrieval-augmented_generation)-style question answering over fully local LLMs:

![RAG](../../../img/rag.png)

To use RAG queries, you need to explcitly define in the config file which LLMs you plan to use query-time:

```yaml
schema:
  movies:
    rag:
      models:
        - handle: Qwen/Qwen2-0.5B-Instruct-GGUF?file=qwen2-0_5b-instruct-q4_0.gguf
          prompt: qwen2
          name: qwen2
    fields:
      title:
        type: text
        search: semantic
        suggest: true
      overview:
        type: text
        search: semantic
        suggest: true
```

Where:
* `handle`: a Huggingface model handle in a format of `namespace`/`model-name`. Optionally may include a `?file=` specifier in a case when model repo contains multiple GGUF files. By default Nixiesearch will pick the lexicographically first file.
* `prompt`: a prompt format, either one of pre-defined ones like `qwen2` and `llama3`, or a raw prompt with `{user}` and `{system}` placeholders.
* `name`: name of this model you will reference in RAG search requests
* `system` (optional): A system prompt for the model.

## Supported prompts

A `qwen2` prompt, which is in fact an alias to the following raw prompt:
```
<|im_start|>user\n{user}<|im_end|>\n<|im_start|>assistant\n
```

A more extended `llama3` prompt is an alias to the next raw one:
```
<|start_header_id|>system<|end_header_id|>

{system}<|eot_id|><|start_header_id|>user<|end_header_id|>

{user}<|eot_id|><|start_header_id|>assistant<|end_header_id|>

```

You can always define your own prompt:
```yaml
schema:
  movies:
    rag:
      models:
        - handle: TheBloke/Mistral-7B-Instruct-v0.2-GGUF
          prompt: "[INST] {user} [/INST]"
          name: mistral7b
```

## Sending requests

For RAG requests, Nixiesearch supports REST and WebSocket protocols:
* REST: much simpler to implement, but blocks till full RAG response is generated.
* WebSocket: can stream each generated response toke, but more complex.

Request format is the same for both protocols:

```json
{
  "query": {
    "multi_match": {
      "fields": ["title", "description"],
      "query": "what is pizza"
    }
  },
  "fields": ["title", "description"],
  "rag": {
    "prompt": "Summarize search results for a query 'what is pizza'",
    "model": "qwen2"
  }
}
```

The `rag` field has the following options:
* `prompt` (string, required): A main instruction for the LLM.
* `model` (string, required): Model name from the `rag.models` [index mapping section](#rag-retrieval-augmented-generation).
* `fields` (string[], optional): A list of fields from the search results documents to embed to the LLM prompt. By default, use all stored fields from the response.
* `topDocs` (int, optional): How many top-N documents to embed to the prompt. By default pick top-10, more documents - longer the context - higher the latency.
* `maxDocLength` (int, optional): Limit each document in prompt by first N tokens. By default, use first 128 tokens.
* `maxResponseLength` (int, optional): Maximum number of tokens LLM can generate. Default 64.

## REST responses

A complete text of the LLM response you can find in a `response` field:

```shell
$> cat rag.json

{
  "query": {
    "multi_match": {
      "fields": ["title"],
      "query": "matrix"
    }
  },
  "fields": ["title"],
  "rag": {
    "prompt": "Summarize search results for a query 'matrix'",
    "model": "qwen2"
  }
}

$> curl -v -XPOST -d @rag.json http://localhost:8080/movies/_search

{
  "took": 3,
  "hits": [
    {
      "_id": "604",
      "title": "The Matrix Reloaded",
      "_score": 0.016666668
    },
    {
      "_id": "605",
      "title": "The Matrix Revolutions",
      "_score": 0.016393442
    },
  ],
  "aggs": {},
  "response": "The following is a list of search results for the query 'matrix'. It includes the following:\n\n- The matrix is the first film in the \"Matrix\" franchise."
}
```

## Websocket responses
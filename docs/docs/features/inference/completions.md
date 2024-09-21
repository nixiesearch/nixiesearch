# LLM chat completions inference

Competion models take an input prompt and generate a response. Internally these are used inside the [RAG search endpoint](../../features/search/rag.md) to summarise search results in a single answer, but you can use them directly through the `/inference/completion/` REST Endpoint.

## Configuring a model

To use an LLM model for inference or for [RAG search](../../features/search/rag.md), you need to explicitly define it in the `inference.completion` [config file section](../../reference/config.md#ml-inference):

```yaml
inference:
  completion:
    your-model-name:
      provider: llamacpp
      model: Qwen/Qwen2-0.5B-Instruct-GGUF
      file: qwen2-0_5b-instruct-q4_0.gguf
      prompt: qwen2
```

Fields:

* `provider`: *required*, *string*. As for `v0.3.0`, only `llamacpp` is supported. Other SaaS providers like OpenAI, Cohere, mxb and Google are on the roadmap.
* `model`: *required*, *string*. A [Huggingface](https://huggingface.co/models) handle, or an HTTP/Local/S3 URL for the model. See [model URL reference](../../reference/url.md) for more details on how to load your model.
* `file`: *optional*, *string*. A file name for the model, if the target model has multiple. A typical case for quantized models.
* `prompt`: *required*, *string*. A prompt format used for the LLM. See [Supported LLM prompts for more details](../search/rag.md#supported-prompts)
* `options`: *optional*, *obj*. A dict of llamacpp-specific options.

See the [`inference.completion` section in config file reference](../../reference/config.md#ml-inference) for more details on other advanced options of providers.

## Sending requests

After you configured your completion LLM model, it becomes available for inference on the `/inference/completion/<your-model-name>` REST endpoint:

```shell
curl -d '{"prompt": "what is 2+2? answer as haiku", "max_tokens": 32}' http://localhost:8080/inference/completion/your-model-name
```

A full request payload looks like this:

```json
{
  "prompt": "what is 2+2? answer as haiku",
  "max_tokens": 32,
  "stream": false
}
```

Fields:

* `prompt`: *required*, *string*. A prompt to process. Before doing the actual inference, the prompt text will be pre-processed using the prompt template for a particular model.
* `max_tokens`: *required*, *string*. Number of tokens to generate. Consider that as a safety net if model cannot stop generating.
* `stream`: *optional*, *boolean*, default `false`. Should the response be in a streaming format? If yes, the server will respond with a sequence of [Server Side Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events). See [Streaming responses](#streaming-responses) section for more details.

## Non-streaming responses

A non-streaming regular HTTP response looks like this:

```json
{
  "output": "Two\nOne, one\nSumming up\nEqual to\n2",
  "took": 191
}
```

Response fields:

* `output`: *required*, *string*. Generated answer for the prompt.
* `took`: *required*, *int*. Number of milliseconds spent processing the request.

## Streaming responses

When a completion request has a `"streaming": true` flag, then Nixiesearch will generate a sequence of [Server Side Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events) for each generated token. This can be used to create a nice ChatGPT interfaces when you see the response being generated in real-time.

So for a search request:

```json
{
  "prompt":"what is 2+2? answer short", 
  "max_tokens": 32, 
  "stream": true
}
```

The server will generate a SSE payload, having a special `Content-Type: text/event-stream` header:

```shell
curl -v -d '{"prompt":"what is 2+2? answer short", "max_tokens": 32, "stream": true}'\
   http://localhost:8080/inference/completion/qwen2

< HTTP/1.1 200 OK
< Date: Fri, 13 Sep 2024 16:29:11 GMT
< Connection: keep-alive
< Content-Type: text/event-stream
< Transfer-Encoding: chunked
< 
event: generate
data: {"token":"2","took":34,"last":false}

event: generate
data: {"token":"+","took":11,"last":false}

event: generate
data: {"token":"2","took":11,"last":false}

event: generate
data: {"token":" =","took":14,"last":false}

event: generate
data: {"token":" ","took":16,"last":false}

event: generate
data: {"token":"4","took":14,"last":false}

event: generate
data: {"token":"","took":13,"last":false}

event: generate
data: {"token":"","took":1,"last":true}
```

The SSE frame has the following syntax:

```json
{
  "token": "wow",
  "took": 10,
  "last": false
}
```

Fields:

* `token`: *required*, *string*. A next generated tokens. To get a full string, you need to concatenate all tokens together.
* `took`: *required*, *int*. How many milliseconds spent generating this token. A first generated token also accounts for prompt processing time, so expect it to always be bigger.
* `last`: *required*, *boolean*. Is this the last token? SSE has no notation of stream end, so you can use this field to assume that the stream is finished.
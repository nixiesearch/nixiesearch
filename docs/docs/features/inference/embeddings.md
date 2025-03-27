# Text embedding inference

Text embeddings map your text inputs into a numerical representation in such a way, so a query and relevant document embeddings should be close in the cosine similarity space. Multiple embeddings providers are supported:

* [Local inference with Sentence-Transformers and ONNX](embeddings/sbert.md) - Any [SBERT](https://sbert.net)-compatible embedding model with local inference using [ONNX](https://onnxruntime.ai) runtime.
* [OpenAI](embeddings/openai.md) - Using external [OpenAI](https://platform.openai.com/docs/api-reference/embeddings/create)-compatible APIs to compute embeddings.
* [Cohere](embeddings/cohere.md) - Using [Cohere v2/embed](https://docs.cohere.com/reference/embed) APIs for embeddings. 

## Configuration file

To use a text embedding model for [search](../search/overview.md#search) or only for inference, you need to configure model in the `inference.embedding` section of a [config file](../../reference/config.md#ml-inference):

```yaml
inference:
  embedding:
    your-model-name:
      model: intfloat/e5-small-v2
```

You can also use OpenAI or any other API-based embedding provider:
```yaml
inference:
  embedding:
    your-model-name:
      provider: openai
      model: text-embedding-3-small
```

A full configuration looks like this:

```yaml
inference:
  embedding:
    your-model-name:
      provider: onnx
      model: intfloat/e5-small-v2
      prompt: # (1)
        query: "query: "
        doc: "passage: "
```

1. Nixiesearch can correctly guess the prompt format for all the moder

Fields:

* `provider`: *optional*, *string*. As for `v0.5.0`, only the `onnx`, `openai` and `cohere` providers are supported. Default - auto-detected.
* `model`: *required*, *string*. A [Huggingface](https://huggingface.co/models) handle, or an HTTP/Local/S3 URL for the model. See [model URL reference](../../reference/url.md) for more details on how to load your model.
* `prompt`: *optional*. A document and query prefixes for asymmetrical models. Default - auto-detected.

See [inference.embedding config file reference](../../reference/config.md#ml-inference) for all advanced options of the ONNX provider.

Nixiesearch supports the following set of models:

* any [sentence-transformers](https://sbert.net) compatible embedding model in the ONNX format. See the [list of supported pre-converted models](embeddings/sbert.md) Nixiesearch already has, or check out [the guide on how to convert your own model](../inference/embeddings/sbert.md#converting-your-own-model).
* As for version `0.3.0`, Nixiesearch only supports the `ONNX` provider for embedding inference. We have OpenAI, Cohere, mxb and Google providers on the roadmap.

!!! note

    Many embedding models (like [E5](https://huggingface.co/intfloat/e5-base-v2), [BGE](https://huggingface.co/BAAI/bge-large-en-v1.5) and [GTE](https://huggingface.co/Alibaba-NLP/gte-large-en-v1.5)) for an optimal predictive performance require a specific prompt prefix for documents and queries. Please consult the model documentation for the expected format.

## Sending requests

After your model is configured in the `inference.embedding` section of the config file, you can send requests to the Nixiesearch endpoint `/inference/embedding/your-model-name`:

```shell
curl -v -d '{"input": [{"text": "hello"}]}' http://localhost:8080/inference/embedding/your-model-name
```

A full request payload looks like this:

```json
{
  "input": [
    {"text": "what is love?", "type": "query"},
    {"text": "baby don't hurt me no more", "type": "document"}
  ]
}
```

* `input`: *list of objects*, *required*. One or more texts to compute embeddings.
* `input.text`: *string*, *required*. A text to compute embedding.
* `input.type`: *string*, *optional*, *default `raw`*. A type of the input: `query`/`document`/`raw`. Some asymmetrical embedding models like E5/GTE/BGE produce different ones for queries and documents. 

!!! note

    When you embed many documents at once, Nixiesearch internally batches them together according to [the inference model configuration](../../reference/config.md#ml-inference). It is OK to sent large chunks of documents via inference API, they will be properly split to internal batches for better performance.

## Embedding responses

A typical embedding response looks like this:

```json
{
  "output": [
    {
      "embedding": [
        -0.43652993,
        0.21856548,
        0.011309982
      ]
    }
  ],
  "took": 4
}
```

Fields:

* `output`: *required*, *list of objects*. Contains document embeddings in the same ordering as in request.
* `output.embedding`: *required*, *list of numbers*. A vector of document embedding values. The dimensionality matches the embedding model configured.
* `took`: *required*, *int*. Number of milliseconds spend processing the response.
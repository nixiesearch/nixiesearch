# Config file 

## Index mapping

todo

## ML Inference

See [ML Inference overview](../features/inference/index.md) and [RAG Search](../features/search/rag.md) for an overview of use cases for inference models.

### Embedding models

Example of a full configuration:

```yaml
inference:
  embedding:
    your-model-name:
      provider: onnx
      model: nixiesearch/e5-small-v2-onnx
      file: model.onnx
      max_tokens: 512
      batch_size: 32
      prompt:
        query: "query: "
        doc: "passage: "
```

Fields:

* `provider`: *required*, *string*. As for `v0.3.0`, only the `onnx` provider is supported.
* `model`: *required*, *string*. A [Huggingface](https://huggingface.co/models) handle, or an HTTP/Local/S3 URL for the model. See [model URL reference](url.md) for more details on how to load your model.
* `prompt`: *optional*. A document and query prefixes for asymmetrical models.
* `file`: *optional*, *string*, default is to pick a lexicographically first file. A file name of the model - useful when HF repo contains multiple versions of the same model.
* `max_tokens`: *optional*, *int*, default `512`. How many tokens from the input document to process. All tokens beyond the threshold are truncated.
* `batch_size`: *optional*, *int*, default `32`. Computing embeddings is a highly parallel task, and doing it in big chunks is much more effective than one by one. For CPUs there are usually no gains of batch sizes beyong 32, but on GPUs you can go up to 1024.

## LLM competion models

Example of a full configuration:

```yaml
inference:
  completion:
    your-model-name:
      provider: llamacpp
      model: Qwen/Qwen2-0.5B-Instruct-GGUF
      file: qwen2-0_5b-instruct-q4_0.gguf
      prompt: qwen2
      system: "You are a helpful assistant, answer only in haiku."
```

Fields:

* `provider`: *required*, *string*. As for `v0.3.0`, only `llamacpp` is supported. Other SaaS providers like OpenAI, Cohere, mxb and Google are on the roadmap.
* `model`: *required*, *string*. A [Huggingface](https://huggingface.co/models) handle, or an HTTP/Local/S3 URL for the model. See [model URL reference](url.md) for more details on how to load your model.
* `file`: *optional*, *string*. A file name for the model, if the target model has multiple. A typical case for quantized models.
* `prompt`: *required*, *string*. A prompt format used for the LLM. See [Supported LLM prompts for more details](../features/search/rag.md#supported-prompts)
* `system`: *optional*, *string*, default empty. An optional system prompt to be prepended to all the user prompts.
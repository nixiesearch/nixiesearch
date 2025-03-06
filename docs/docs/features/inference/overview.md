# ML model inference

Nixiesearch also exposes [embeddings](embeddings.md) and [LLM chat completions](completions.md) APIs. These APIs used internally for [semantic search](../search/query.md) and [RAG](../search/rag.md) tasks, but you can have a raw access to them. Typical use cases:

* adding an additional safety filter on top of RAG responses by prompting an LLM for `does {answer} answers the question {question}?`.
* preventing LLM hallucinations by embedding both question and RAG answer and computing cosine similarity between them. Question and a proper answer should be close to each other.

## Adding models for inference

To use an embedding or chat model for inference, you need to explicitly define it in the [config file](../../reference/config.md) `inference` section:

```yaml
inference:
  embedding:
    <model-name>:
      provider: onnx
      model: nixiesearch/e5-small-v2-onnx
      prompt:
        query: "query: "
        doc: "passage: "
  completion:
    <model-name>:
      provider: llamacpp
      model: Qwen/Qwen2-0.5B-Instruct-GGUF
      file: qwen2-0_5b-instruct-q4_0.gguf
      prompt: qwen2
```

The inference section (and embedding/completion sub-sections also) are optional and not required if you do only lexical search. See a full [config file reference](../../reference/config.md#ml-inference) for all the configuration options and a list of supported [LLM models](../inference/completions.md) and [embeddings](../inference/embeddings.md).

!!! note

    Inference for both embedding and completion models can also be done on [GPUs](../../deployment/distributed/gpu.md).

## Supported endpoints

Nixiesearch supports the following inference endpoints:

* [`/inference/embeddings/<model-name>`](embeddings.md) for [text embeddings](embeddings.md) to translate text inputs to numerical representations.
* [`/inference/completions/<model-name>`](completions.md) for [LLM chat completions](completions.md) to prompt the underlying model with natural language questions.


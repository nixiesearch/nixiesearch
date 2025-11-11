# OpenAI API embedding inference


## Authentication

To provide an API key to authenticate to OpenAI API, use the `OPENAI_API_KEY` environment variable when starting Nixiesearch:

```shell
docker run -it -e OPENAI_API_KEY=<thekey> nixiesearch/nixiesearch <opts>
```

## Usage 

Nixiesearch supports any [OpenAI-compatible]() embedding endpoints (e.g llamacpp). To define an OpenAI embedding model in the config file, use the following snippet:

```yaml
inference:
  embedding:
    <model-name>:
      provider: openai
      model: text-embedding-3-small
```

The full configuration with all default options:

```yaml
inference:
  embedding:
    <model-name>:
      provider: openai
      model: text-embedding-3-small
      timeout: 2000ms
      endpoint: "https://api.openai.com/"
      dimensions: null
      batch_size: 32
      retry: 1
```

Parameters:

* **timeout**: optional, duration, default 2s. External APIs might be slow sometimes.  
* **endpoint**: optional, string, default "https://api.openai.com/". You can use alternative API or EU-specific endpoint.
* **dimensions**: optional, int, default empty. For [matryoshka](https://huggingface.co/blog/matryoshka) models, how many dimensions to return.
* **batch_size**: optional, int, default 32. Batch size for calls with many documents.
* **retry**: optional, int, default 1. Number of retries to perform when API fails.

See [Config file reference](../../../reference/config.md) for more details on creating a config file. 
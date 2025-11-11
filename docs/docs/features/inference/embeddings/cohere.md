# Cohere API embedding inference


## Authentication

To provide an API key to authenticate to Cohere API, use the `COHERE_API_KEY` environment variable when starting Nixiesearch:

```shell
docker run -it -e COHERE_API_KEY=<thekey> nixiesearch/nixiesearch <opts>
```

## Usage

To define a Cohere embedding model in the config file, use the following snippet:

```yaml
inference:
  embedding:
    <model-name>:
      provider: cohere
      model: embed-english-v3.0
```

The full configuration with all default options:

```yaml
inference:
  embedding:
    <model-name>:
      provider: cohere
      model: embed-english-v3.0
      timeout: 2000ms
      endpoint: "https://api.cohere.com/"
      batch_size: 32
      retry: 1
```

Parameters:

* **timeout**: optional, duration, default 2s. External APIs might be slow sometimes.
* **batch_size**: optional, int, default 32. Batch size for calls with many documents.
* **retry**: optional, int, default 1. Number of retries to perform when API fails.

See [Config file reference](../../../reference/config.md) for more details on creating a config file. 
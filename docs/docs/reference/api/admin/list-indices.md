# List indices 

A system REST API call to `/_indexes` can be used to list all active indices on the current node. Technically it is very similar to the [_config](config.md) call, but without per-index configuration exposed.

> Note: there is an alias `/_indices` available for this REST method.

## Usage

With cURL:

```shell
$> curl http://localhost:8080/_indexes
{
  "indexes": [
    "movies"
  ]
}

```
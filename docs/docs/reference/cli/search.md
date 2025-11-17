# nixiesearch search

The `search` command runs Nixiesearch in searcher mode, handling search queries against pre-built indexes.

## Usage

```bash
nixiesearch search --config <config.yml> [--api <mode>]
```

## Options

- `--config`, `-c` - Path to configuration file (required)
- `--api` - API mode: `http` (default) or `lambda` (optional)

## API Modes

### HTTP Mode (Default)

Runs as a traditional HTTP server:

```bash
nixiesearch search --config config.yml --api http
# or simply
nixiesearch search --config config.yml
```

The searcher listens on the configured port (default 8080) and serves the REST API.

### Lambda Mode

Runs as an AWS Lambda function:

```bash
nixiesearch search --config config.yml --api lambda
```

In Lambda mode, Nixiesearch integrates with the AWS Lambda Runtime API and converts API Gateway V2 HTTP events to search requests. This mode requires the `AWS_LAMBDA_RUNTIME_API` environment variable (automatically set by Lambda).

See the [Lambda deployment guide](../../deployment/distributed/lambda.md) for details.

## Remote Config Loading

Nixiesearch supports loading config files from remote locations:

* **S3**: `nixiesearch search -c s3://bucket/prefix/conf.yml`
* **HTTP/HTTPS**: `nixiesearch search -c https://example.com/config.yml`
* **Local files**: `nixiesearch search -c config.yml` or `nixiesearch search -c file:///path/config.yml`

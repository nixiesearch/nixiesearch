# AWS Lambda Deployment

You can run Nixiesearch searcher as an AWS Lambda function for serverless deployments. This works well for variable traffic patterns where you'd rather pay per request than keep servers running around the clock.

!!! warning "Searcher Only"
    Lambda deployment only works for searcher nodes. You'll need to run the indexer separately on Kubernetes, ECS, or EC2.

## Image Selection

Nixiesearch offers two Docker image types with different tradeoffs:

### JDK Images (`nixiesearch/nixiesearch:latest`)

These are the standard production images with full feature support:

- Complete ONNX embedding and llamacpp LLM inference
- Larger image size (~500MB-1GB) with 1-3 second cold starts
- Use these for semantic search with local embedding models

### Native Images (`nixiesearch/nixiesearch:latest-native`)

GraalVM native images optimized for serverless:

- Zero JVM warmup time, smaller footprint (~50-100MB), sub-500ms cold starts
- **Highly experimental** - stability not guaranteed
- No ONNX embedding or llamacpp support - API-based inference only
- Good for lexical search or when using API-based embeddings (OpenAI, Cohere)

## Storage Options

Lambda searchers need access to pre-built indexes. You have two choices:

### S3 Storage

Index segments live in S3, synced to Lambda ephemeral storage on initialization:

```yaml
schema:
  movies:
    store:
      distributed:
        searcher:
          local:
            disk:
              path: /tmp/index
        indexer:
          memory:
        remote:
          s3:
            bucket: your-nixiesearch-indexes
            prefix: movies
            region: us-west-2
```

Cold starts include index sync time (2-10 seconds depending on index size), but search queries run fast since the index can use page cache. Doesn't require VPC configuration.

Indexes are continuously updated - Nixiesearch pulls new segments from S3 when the indexer writes new documents, so searchers stay in sync automatically.

### EFS Storage

Lambda mounts an EFS volume where the indexer writes directly:

```yaml
schema:
  movies:
    store:
      local:
        path: /mnt/efs/indexes/movies
```

No sync wait on cold start, but search queries have higher latency (50-100ms) due to network I/O. Requires VPC setup with proper security groups.

### Index Size Considerations

Nixiesearch uses memory-mapped I/O (mmap in JDK builds, NIO in native builds) to access index data, so there's no hard limit on index size. For native builds, you can explicitly configure `directory: "nio"` in the [index configuration](../../reference/config.md#index-configuration) if needed. However, **for semantic search with HNSW vector indexes**, if your index doesn't fit in available RAM, expect search latency to jump roughly 10x due to random disk reads during graph traversal.

Note that Lambda has a **practical 3GB memory limit** (not 10GB as some docs suggest) unless you submit a quota increase request through AWS Support.

## Resource Configuration

Lambda allocates CPU proportionally to memory. For search workloads, max out at **3008MB** to get the fastest CPU available (~1.8 vCPUs).

```yaml
MemorySize: 3008
Timeout: 60
EphemeralStorage:
  Size: 2048  # Only needed for S3-based indexes
```

For S3 storage, allocate ephemeral storage roughly equal to your index size. For EFS storage, ephemeral storage can be minimal.

We don't recommend running ONNX embedding inference on Lambda - the CPU is too limited. Use API-based embedding providers (OpenAI, Cohere) instead.

## Networking

By default, Lambda functions have internet access. If you're using API-based embeddings and don't need VPC resources, you're all set.

If you put Lambda in a VPC (for EFS or private resources), you'll need to set up proper routing for external API calls:

```
Lambda (private subnet) → NAT Gateway (public subnet) → Internet Gateway
```

## IAM Permissions

For S3-based deployment:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:ListBucket"],
      "Resource": [
        "arn:aws:s3:::your-nixiesearch-indexes",
        "arn:aws:s3:::your-nixiesearch-indexes/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:*:*:*"
    }
  ]
}
```

For VPC deployment, add:

```json
{
  "Effect": "Allow",
  "Action": [
    "ec2:CreateNetworkInterface",
    "ec2:DescribeNetworkInterfaces",
    "ec2:DeleteNetworkInterface"
  ],
  "Resource": "*"
}
```

For EFS storage, add:

```json
{
  "Effect": "Allow",
  "Action": [
    "elasticfilesystem:ClientMount",
    "elasticfilesystem:ClientWrite"
  ],
  "Resource": "arn:aws:elasticfilesystem:region:account-id:file-system/fs-xxxxx"
}
```

## Configuration

Here's a typical config for Lambda with S3 storage and API-based embeddings:

```yaml
inference:
  embedding:
    openai-embed:
      provider: openai
      model: text-embedding-3-small

schema:
  movies:
    store:
      distributed:
        searcher:
          local:
            disk:
              path: /tmp/index
        indexer:
          memory:
        remote:
          s3:
            bucket: your-nixiesearch-indexes
            prefix: movies
            region: us-west-2
    fields:
      title:
        type: text
        search:
          lexical:
            analyze: english
          semantic:
            model: openai-embed
      overview:
        type: text
        search:
          lexical:
            analyze: english
```

API keys are passed via Lambda environment variables (see deployment example below).

See the [configuration reference](../../reference/config.md) for all available options.

## Deployment

!!! note "Private ECR Registry Required"
    AWS Lambda requires container images to be stored in a **private ECR registry in the same region** as your Lambda function. While Nixiesearch images are hosted in the public ECR registry, Lambda cannot use them directly.

    To deploy Nixiesearch on Lambda:

    1. Pull the public image:
       ```bash
       docker pull public.ecr.aws/nixiesearch/nixiesearch:latest
       ```

    2. Tag it for your private ECR registry:
       ```bash
       docker tag public.ecr.aws/nixiesearch/nixiesearch:latest \
         XXXXXXX.dkr.ecr.us-east-1.amazonaws.com/nixiesearch:latest
       ```

    3. Authenticate and push to your private ECR:
       ```bash
       aws ecr get-login-password --region us-east-1 | \
         docker login --username AWS --password-stdin XXXXXXX.dkr.ecr.us-east-1.amazonaws.com
       docker push XXXXXXX.dkr.ecr.us-east-1.amazonaws.com/nixiesearch:latest
       ```

    Replace `XXXXXXX` with your AWS account ID and `us-east-1` with your Lambda's region. See the [Container Registries](../overview.md#container-registries) section for more details.

Using Terraform:

```hcl
resource "aws_lambda_function" "nixiesearch" {
  function_name = "nixiesearch-searcher"
  role          = aws_iam_role.lambda_role.arn
  package_type  = "Image"
  image_uri     = "XXXXXXX.dkr.ecr.us-east-1.amazonaws.com/nixiesearch:latest"
  memory_size   = 3008
  timeout       = 60

  image_config {
    command = ["search", "--config", "/config.yml", "--api", "lambda"]
  }

  ephemeral_storage {
    size = 2048
  }

  environment {
    variables = {
      OPENAI_API_KEY = var.openai_api_key
    }
  }
}

resource "aws_apigatewayv2_api" "nixiesearch" {
  name          = "nixiesearch-api"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_integration" "nixiesearch" {
  api_id           = aws_apigatewayv2_api.nixiesearch.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.nixiesearch.invoke_arn
  payload_format_version = "2.0"
}
```

Note: Nixiesearch requires **API Gateway V2 (HTTP API)**, not the older REST API or V1.

## Usage

First, build your indexes using a separate indexer process:

```bash
docker run -it nixiesearch/nixiesearch:latest index file \
  --config /config.yml \
  --index movies \
  --url /data/movies.jsonl
```

Deploy the Lambda function:

```bash
terraform apply
```

Test it:

```bash
curl -X POST "https://your-api-id.execute-api.region.amazonaws.com/v1/index/movies/search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {"match": {"title": "matrix"}},
    "fields": ["title"],
    "size": 5
  }'
```

## Next Steps

- Set up [monitoring](prometheus.md) with CloudWatch metrics
- Configure [autoscaling](../../tutorial/autoscaling.md) with provisioned concurrency
- Review [search features](../../features/search/overview.md) and [API usage](../../api.md)
- Implement [backup procedures](../../tutorial/backup.md) for your S3 indexes

For other deployment options, see [Kubernetes deployment](overview.md) and [standalone deployment](../standalone.md).

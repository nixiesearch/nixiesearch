# Cross-Encoder Reranking

Cross-encoder models are powerful neural ranking models that can significantly improve search relevance by reranking initial search results. Unlike bi-encoder models that encode queries and documents separately, cross-encoders process query-document pairs jointly, enabling more sophisticated relevance scoring. 

Cross-encoders complement other ranking methods like [RRF (Reciprocal Rank Fusion)](./rrf.md) and work as part of Nixiesearch's comprehensive [search query system](../overview.md). See [sentence-transformers Cross-Encoder docs](https://sbert.net/docs/quickstart.html#cross-encoder) for more details on the underlying technology.

## How Cross-Encoders Work

Cross-encoders take a query and document as input, concatenate them, and output a relevance score. This joint processing allows the model to understand complex relationships between query terms and document content, leading to more accurate relevance judgments.

The typical workflow is:

1. **Initial Retrieval**: Use a fast retrieval method ([semantic](../retrieve/semantic.md), [lexical](../retrieve/match.md), or [hybrid](../retrieve/dis_max.md)) to get candidate documents
2. **Reranking**: Apply the cross-encoder to score and rerank the top candidates
3. **Final Results**: Return the reranked documents with improved relevance ordering

## Configuration

### Model Setup

Configure cross-encoder models in your [configuration file](../../../../reference/config.md) under the `inference.ranker` section:

```yaml
inference:
  ranker:
    ce_model:
      provider: onnx
      model: cross-encoder/ms-marco-MiniLM-L6-v2
      max_tokens: 512
      batch_size: 32
      device: cpu
```

**Configuration Options:**

- `provider`: Model provider (currently only `onnx` supported)
- `model`: HuggingFace model identifier or local path
- `max_tokens`: Maximum sequence length (default: 512)
- `batch_size`: Inference batch size (default: 32)
- `device`: Processing device (`cpu` or `gpu`) - see [inference overview](../../../inference/overview.md) for hardware requirements
- `file`: Optional path to custom ONNX model file

Popular cross-encoder models to consider:

- `cross-encoder/ms-marco-MiniLM-L6-v2`: Fast, general-purpose ranking model, English only.
- [`jinaai/jina-reranker-v2-base-multilingual`](https://huggingface.co/jinaai/jina-reranker-v2-base-multilingual): Slower, but much more precise multilingual ranker.

Nixiesearch supports any sentence-transformer cross-encoder models in ONNX format. See the [Speeding up Inference > ONNX](https://sbert.net/docs/sentence_transformer/usage/efficiency.html#onnx) section of [SBERT](https://sbert.net) docs for more details on how to convert your own model.

## Query Syntax

Basic Cross-Encoder query:

```json
{
  "query": {
    "cross_encoder": {
      "model": "ce_model",
      "query": "artificial intelligence applications",
      "doc_template": "{{ title }} {{ description }}",
      "retrieve": {
        "semantic": {
          "title": "AI machine learning"
        }
      }
    }
  }
}
```

Parameters:

- `model`: Reference to configured cross-encoder model
- `query`: Query text to compare against documents
- `doc_template`: Jinja template for rendering document content
- `retrieve`: Initial retrieval query (can be any [query type](../overview.md) - [semantic](../retrieve/semantic.md), [match](../retrieve/match.md), [bool](../retrieve/bool.md), etc.)
- `rank_window_size`: Number of documents to retrieve before reranking (optional)

!!! note

    **Important**: The `query` parameter requires explicit text that represents the user's search intent. While the `retrieve` query can be any query type (including wildcards, filters, or complex boolean queries), the cross-encoder needs a clear text representation of what the user is looking for. This text query is usually a copy of the main search terms from your retrieval query, but cannot always be extracted automatically - especially when using non-textual queries like category filters or wildcards.

## Document Templates

Document templates use Jinja syntax to combine multiple [document fields](../../../indexing/mapping.md) into the text passed to the cross-encoder:

Simple template:

```json
{
  "doc_template": "{{ title }}"
}
```

Multi-field template:

```json
{
  "doc_template": "Title: {{ title }}\nDescription: {{ description }}\nCategories: {{ categories }}"
}
```

Conditional template:

```json
{
  "doc_template": "{{ title }}{% if description %} - {{ description }}{% endif %}"
}
```

The system automatically extracts required fields from your template based on your [index mapping](../../../indexing/mapping.md), so you only need to specify the template without listing fields separately.

## Examples

### E-commerce Product Search
This example uses [multi-match queries](../retrieve/multi_match.md) for initial retrieval:

```json
{
  "query": {
    "cross_encoder": {
      "model": "ce_model",
      "query": "wireless bluetooth headphones",
      "doc_template": "{{ title }} {{ brand }} {{ description }}",
      "rank_window_size": 50,
      "retrieve": {
        "multi_match": {
          "query": "wireless bluetooth headphones",
          "fields": ["title^2", "description", "brand"]
        }
      }
    }
  }
}
```

### Knowledge Base Search
This example uses [semantic search](../retrieve/semantic.md) for initial retrieval:

```json
{
  "query": {
    "cross_encoder": {
      "model": "ce_model",
      "query": "how to configure SSL certificates",
      "doc_template": "{{ title }}\n{{ content }}",
      "retrieve": {
        "semantic": {
          "content": "SSL certificate configuration setup"
        }
      }
    }
  }
}
```

### Hybrid Retrieval with Cross-Encoder Reranking
This example combines [semantic](../retrieve/semantic.md) and [lexical](../retrieve/match.md) search using [RRF (Reciprocal Rank Fusion)](./rrf.md):

```json
{
  "query": {
    "cross_encoder": {
      "model": "ce_model",
      "query": "machine learning model deployment",
      "doc_template": "{{ title }} {{ abstract }}",
      "retrieve": {
        "rrf": {
          "queries": [
            {"semantic": {"abstract": "ML model deployment"}},
            {"match": {"title": "machine learning deployment"}}
          ]
        }
      }
    }
  }
}
```

!!! note

    Cross-encoders expect a single, unified set of documents for reranking. For hybrid search scenarios where you want to combine results from multiple retrieval methods (lexical and semantic), you must first merge them using techniques like [RRF](./rrf.md) or [disjunction max](../retrieve/dis_max.md) before applying cross-encoder reranking.

## Performance Considerations

### Window Size Optimization

Use `rank_window_size` to balance relevance and performance:

```json
{
  "query": {
    "cross_encoder": {
      "model": "ce_model",
      "query": "search query",
      "doc_template": "{{ title }}",
      "rank_window_size": 100,
      "retrieve": {
        "semantic": {"title": "initial query"}
      }
    }
  }
}
```

- **Small window (20-50)**: Faster inference, may miss relevant documents
- **Medium window (50-100)**: Good balance for most use cases
- **Large window (100+)**: Better recall, slower performance

### Batch Size Tuning

Configure batch size based on your hardware:
- **CPU**: 8-32 documents per batch
- **GPU**: 32-128 documents per batch
- **Memory-constrained**: Reduce batch size if getting OOM errors

## Best Practices

1. **Template Design**: Include the most relevant fields that help distinguish document relevance
2. **Initial Retrieval**: Use efficient retrieval methods ([semantic](../retrieve/semantic.md)/[lexical](../retrieve/match.md)) to get good candidates
3. **Window Sizing**: Start with 50-100 documents and adjust based on performance needs
4. **Model Selection**: Choose models appropriate for your domain (general vs. specialized)
5. **Performance**: Cross-encoder inference is expensive; use appropriate `rank_window_size` and `batch_size` for your hardware

## Integration with Other Features

### With Filters
Cross-encoder reranking works seamlessly with [search filters](../../filter.md) to first narrow down results before reranking:

```json
{
  "query": {
    "cross_encoder": {
      "model": "ce_model",
      "query": "laptop gaming",
      "doc_template": "{{ title }} {{ specs }}",
      "retrieve": {
        "match": {"title": "laptop"}
      }
    }
  },
  "filter": {
    "term": {"category": "electronics"}
  }
}
```

### With Aggregations
Cross-encoder reranking works seamlessly with [facets and aggregations](../../facet.md), as aggregations are computed on the initial retrieval results before reranking. This allows you to get both relevant reranked results and accurate facet counts.

### With RAG (Retrieval-Augmented Generation)
Cross-encoders are particularly useful in [RAG pipelines](../../rag.md) where high-quality document ranking directly impacts the quality of generated responses.
# Index mapping

Index mapping is used to describe how you plan to search for documents in Nixiesearch. Each time a document arrives to the indexer, it iterates over all fields in the payload, and maps them to internal index structures.

To create an index mapping, create a block in the `schema` section of the [config file](../../reference/config.md):

```yaml
schema:
  # a first index
  movies:
    fields:
      title:
        type: text
        search: 
          lexical:
            analyze: english
  # a second index
  songs:
    fields:
      author:
        type: text
        search: 
          lexical:
            analyze: english
```

In the index mapping above we defined two indexes `movies` and `songs` with a single text field indexed for a traditional lexical search.

!!! note

    As Nixiesearch avoids having state within the [searcher](../../reference/cli/search.md), you can only create indexes statically by defining them in the config file. If you plan to dynamically alter configuration of Nixiesearch cluster when running on [Kubernetes](../../deployment/distributed/kubernetes.md), consider using tools like [ArgoCD](https://argo-cd.readthedocs.io/en/stable/) to gradually change configuration.

## Mapping fields

A `fields` block in the index mapping explicitly lists all document fields Nixiesearch uses and stores in the index. For example, when you index the following JSON document:

```json
{
  "title": "The Matrix",
  "year": 1999,
  "genre": "sci-fi"
}
```

You should define field mappings for fields `title`, `year` and `genre` the way you plan to query them. Fields you don't use (but that still present in the JSON document) you can omit, and Nixiesearch will ignore them. 

Each field definition has a set of common fields, and an extra list of per-type specific ones:

```yaml
schema:
  movies:
    fields:
      title:          # field name
        type: text    # required, field type
        store: true   # optional, default true
        sort: false   # optional, default false
        facet: false  # optional, default false
        filter: false # optional, default false 
```

So by default all fields in Nixiesearch are:

* `store: true`: you can retrieve raw field value back from Nixiesearch.
* `sort: false`: you can't sort over field without explicitly marking a field as sortable. 
* `facet: false`: you can't perform facet aggregations by default.
* `filter: false`: you cannot filter over fields by default.

!!! warning

    Sorting, faceting and filtering requires multiple type-specific index data structures to be maintained in the background, so only mark fields as sortable/facetable/filterable if you plan to use them so.

Multiple field types are supported, so the `type` parameter can be one of the following:

* [Text fields](#text-field-mapping): `text` and `text[]`. Unlike other Lucene-based search engines where all fields are implicitly repeatable, we distinguish between single and multi-value fields.
* [Numerical fields](#numerical-fields): `int`, `long`, `double`, `float`, `bool`, `geopoint`. You cannot search over numerical fields (unless you treat them as strings), but you can [filter](../search/filter.md), [facet](../search/facet.md) and [sort](../search/sort.md)!
* [Media fields](#media-fields): `image`. A special field type for [multi-modal search](types/images.md).
* Date fields: `date` and `datetime`. 

### Wildcard fields

To allow more dynamism in index schema, you can use `*` wildcard placeholder in field names:

```yaml
schema:
  movies:
    extra_*:
      type: text
      search:
        type: lexical
        language: en
```

So all fields matching the wildcard pattern are going to be treated according to the schema. Wildcard fields have minor limitations:

* only a single `*` placeholder is allowed.
* you cannot have a non-wildcard field defined matching a wildcard pattern (e.g. having both a regular `title_string` field and a wildcard `*_string` in the same index).

Wildcard fields can be used in the `fields` block of search request to get multiple fields from the document at once:

```json
{
  "query": {
    "match_all": {}
  },
  "fields": ["extra_*"]
}
```

The [`multi_match`](../search/query/retrieve/multi_match.md) search operator also supports matching over wildcard fields during search.

## Text field mapping

Apart from common `type`, `store`, `filter`, `sort` and `facet` parameters, text fields have a set of other search-related options:

* A field can be `search`able, with [lexical](#lexical-search), [semantic](#semantic-search) and [hybrid retrieval](#hybrid-search).
* You can use field contents to generate search [autocomplete suggestions](../autocomplete/index.md)

```yaml
schema:
  movies:
    fields:
      title:
        type: text
        search: 
          lexical: # make only lexical index
            analyze: english
        suggest: false
      overview:
        type: text
        search: # now semantic search!
          semantic:
            model: e5-small # a name of the model in the inference section
inference:
  embedding:
    # the model used to embed documents
    e5-small:
      model: nixiesearch/e5-small-v2-onnx

```

### Lexical search

To define a lexical-only search over a field, you mark it as `search.lexical` and optionally define a [target language](../../reference/languages.md):

```yaml
schema:
  movies:
    fields:
      title:
        type: text
        search: 
          lexical:
            analyze: english # optional, default: generic
      overview:
        type: text
        search:
          lexical: {} # use default generic analyzer
```

For a better search quality, it's advised for lexical search to define the `analyze` of the field: this way Nixiesearch will use a Lucene language-specific analyzer. By default, the `StandardAnalyzer` is used.

See all supported languages in the [supported languages](../../reference/languages.md) section.

### Semantic search

To use an embedding-based semantic search, mark a text field as `search.semantic` and define [an embedding model](../inference/embeddings.md) to use:

```yaml
schema:
  movies:
    fields:
      title:
        type: text
        search: 
          semantic:          
            model: e5-small # a model name from the inference section

# each model you plan to use for embedding
# should be explicitly defined
inference:
  embedding:
    e5-small:
      model: nixiesearch/e5-small-v2-onnx
```

Nixiesearch supports both [self-hosted ONNX embedding models](../inference/embeddings.md) and [external embedding APIs](../inference/embeddings.md).

### Hybrid search

A hybrid search is a combination of lexical and semantic retrieval for a single field. Hybrid field mapping is a union of both  `semantic` and `lexical` mappings:

```yaml
schema:
  movies:
    fields:
      title:
        type: text
        language: en              # optional, default: generic
        search: 
          semantic:      
            model: e5-small         # a ref to the inference model name
          lexical:
            analyze: english

inference:
  embedding:
    e5-small:
      model: nixiesearch/e5-small-v2-onnx
```

For hybrid retrieval, Nixiesearch performs two search queries in parallel for both methods, and then mixes search results with [Reciprocal Rank Fusion](../search/overview.md#hybrid-search-with-reciprocal-rank-fusion).

## Numerical fields

Numerical fields of types `int`, `float`, `double`, `long` and `bool` cannot be searchable, so only have a common set of options:

```yaml
schema:
  movies:
    fields:
      year: 
        type: int
        sort: true
        filter: true
        facet: true
      in-theaters:
        type: bool
        sort: true
        filter: true
        facet: true
```

In the index mapping above we marked `year` and `in-theaters` fields as sortable, filterable and facetable.

## Media fields

!!! warning

    Image field support is planned for the `v0.7` release.

## Mapping options

An index mapping can optionally also include many other index-specific settings like:

* [index alias](#index-aliases): secondary name for an index
* [RAG settings](#rag-settings): prompt template and GenAI model settings.
* [store](#store-settings): how index is stored and synchronized.

### Index aliases

Nixiesearch indexes cannot be easily renamed (due to cluster state being immutable), but you can give them extra names as an alias:

```yaml
schema:
  movies:
    alias: 
    - films
    - series
    fields:
      title:
        type: text
```

When an index has an alias, then it can be queried with multiple names:

```shell
curl -XPOST -d '{}' http://localhost/films/_search
```

Main use-case for aliases is ability to promote indexes:

1. You reindex your document corpus to a timestamped index `index_20240801`
2. You perform your QA tasks validating that nothing is broken,
3. You promote the `index_20240801` to `prod` by giving it alias `prod`

So your end customers won't need to change their REST endpoint address and always target the same index.

!!! warning

    Aliases should be unique, so a single alias cannot belong to multiple indexes.

### RAG settings

To use RAG queries, you need to explicitly define in the [config file](../../reference/config.md#ml-inference) which LLMs you plan to use query-time:

```yaml
inference:
  embedding:
    # Used for semantic retrieval
    e5-small:
      model: nixiesearch/e5-small-v2-onnx
  completion:
    # Used for summarization
    qwen2:
      provider: llamacpp
      model: Qwen/Qwen2-0.5B-Instruct-GGUF
      file: qwen2-0_5b-instruct-q4_0.gguf
    
schema:
  movies:
    fields:
      title:
        type: text
        search: 
          semantic:
            model: e5-small
        suggest: true
      overview:
        type: text
        search: 
          semantic:
            model: e5-small
        suggest: true
```

Where:

* `model`: a Huggingface model handle in a format of `namespace`/`model-name`.
* `file`: an optional model file name if there are multiple. By default Nixiesearch will pick the lexicographically first GGUF/ONNX file. 
* `name`: name of this model you will reference in RAG search requests


See [RAG reference](../search/rag.md) and [ML model inference](../inference/overview.md) sections for more details.

### Store settings

When ran in [distributed mode](../../deployment/distributed/overview.md), you can configure the way index is stored:

```yaml
schema:
  # a regular disk-based index storage
  # OK for standalone and single-node deployments
  # NOT OK for distributed mode
  movies_local_disk:
    fields:
      title:
        type: text
    store:
      local:
        disk: 
          path: file:///path/to/index
  # ephemeral in-memory index
  # all data will be lost upon server restart
  # OK for development, NOT OK for everything else
  movies_inmem:
    fields:
      title:
        type: text
    store:
      local:
        memory:
  # S3-backed index
  # OK for distributed mode, NOT OK for standalone
  movies_s3:
    store:
      distributed:
        searcher:
          memory:
        indexer:
          memory:
        remote:
          s3:
            bucket: bucket
            prefix: e2e
            region: us-east-1
            endpoint: http://localhost:4566/

    fields:
      title:
        type: text
```

See [distributed persistence reference](../../deployment/distributed/persistence/index.md) for more details.

## Next steps

To continue your journey with setting up indexing, follow to the next sections:

* [Document format](format.md) of JSON documents you index.
* Supported [field types](types/overview.md).
* Indexing [REST API](../../api.md) and [nixiesearch index](../../reference/cli/index.md) CLI app reference for offline indexing.
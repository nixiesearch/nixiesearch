# Index mapping

Index mapping is used to describe how you plan to search for documents in Nixiesearch. Each time a document arrives to the indexer, it iterates over all fields in the payload, and maps them to internal index structures.

To create an index mapping, create a block in the `schema` section of the [config file](../../reference/config.md):

```yaml
schema:
  movies:
    fields:
      title:
        type: text
        search: lexical
  songs:
    fields:
      author:
        type: text
        search: semantic
```

In the index mapping above we defined two indexes `movies` and `songs` with a single text field.

!!! note

    As Nixiesearch avoids having state within the [searcher](../../reference/cli/search.md), you can only create indexes statically by defining them in the config file. If you plan to dynamically alter configuration of Nixiesearch cluster when running on [Kubernetes](../../deployment/distributed/kubernetes.md), consider using tools like [ArgoCD](https://argo-cd.readthedocs.io/en/stable/) to gradually change configuration.

    name: IndexName,
    alias: List[Alias] = Nil,
    config: IndexConfig = IndexConfig(),
    rag: RAGConfig = RAGConfig(),
    store: StoreConfig = StoreConfig(),
    cache: IndexCacheConfig = IndexCacheConfig(),
    fields: Map[String, FieldSchema[? <: Field]]

## Mapping fields

A `fields` block in the index mapping explcitly lists all document fields Nixiesearch uses and stores in the index. Each field definition has a set of common fields, and an extra list of per-type specific ones:

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

Multiple field types are supported

## Mapping options

An index mapping
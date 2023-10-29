# Building an index

Nixiesearch index is a searchable group of documents sharing the same structure. 

There are two ways to create an index:

* defining a [static index mapping](index.md#static-index-mapping) in a config file, when you manually define which fields your documents have, their types and how they are going to be searched. This allows more control over the way documents are stored and searched, but unfortunately requires reading this documentation.
* ingesting your [JSON documents](../reference/api/index/document-format.md) as-is without any mapping, and make Nixiesearch deduce the [dynamic index mapping](index.md#dynamic-index-mapping) on the fly from your documents format.

## Dynamic index mapping

Dynamic mapping deduces subjectively the best way of storing your documents in the index based on its [JSON structure](../reference/api/index/document-format.md):

* **flat documents** are mapped as is to index fields.
* **arrays** are different from singular fields, and use different underlying data structures.
* **nested documents** are flattened.

For example, the following document:

```json
{
  "_id": 1,
  "title": "socks",
  "colors": ["red", "black"],
  "price": 100.0,
  "meta": {"asin": "AAA123"},
  "variants": [
    {"id": "v1", "name": "long socks"},
    {"id": "v2", "name": "short socks"}
  ]
}
```

Will generate this mapping:
```yaml
search:
  my-index:
    fields:
      - title:
        type: text
      - colors: 
        type: text[]
      - price:
        type: float
      - meta.asin: # flattened into a non-repeated nested field
        type: text
      - variants.id: # as it's an array of documents,
        type: text[] # flattened into a text[] field
      - variants.name:
        type: text[]
```

To see how Nixiesearch generates mappings for your documents, you can hit the `GET /<index>/_mapping` endpoint after indexing a single document:

```bash
curl -XPOST -d '{"title": "a", "color": ["red"], "meta": {"asin":"a"}}'\
  http://localhost:8080/dev/_index
```

And after that, you can query the generated mapping with the following command:

```bash
curl http://localhost:8080/dev/_mapping
```

which for the sample document above will emit a nice mapping in a JSON format (shortened for better readability) you can use without writing a config file with an explicit one:

```json
{
  "name": "dev",
  "fields": {
    "color": {
      "type": "text[]",
      "name": "color"
    },
    "meta.asin": {
      "type": "text",
      "name": "meta.asin"
    },
    "title": {
      "type": "text",
      "name": "title"
    },
    "_id": {
      "type": "text",
      "name": "_id",
      "search": { "type": "disabled" }
    }
  }
}
```

### Problems of dynamic mapping

Nixiesearch is not perfect in deducing a proper mapping and makes a lot of conservative assumptions about how documents are going to be accessed later.

* Each detected text field is marked as searchable, [facetable](../reference/api/search/facet.md) and [filterable](../reference/api/search/filter.md). Each of these features requires a lot of underlying storage and compute, so statically enabling these will save you **a lot** of resources.
* All the fields in the JSON document are mapped to fields, even the ones not later used for search. Explicit mapping allows you to omit unused fields.
* Deduced types are not always perfect. It's not possible to distinguish a specific numeric type (int/float/long/double) from the JSON payload, so we always use double.

To alleviate all such issues, for any non-playground deployment prefer the static index mapping.

## Static index mapping

To define a static index mapping, you need to add an index-specific block to the `search` section of the [configuration file](../reference/config/mapping.md):

```yaml
search:
  my-first-index:
    fields:
      title:
        type: text
      price:
        type: float 
```

In the example above we defined an index `my-first-index` with two fields title and price.

Each field definition in a static mapping has two groups of settings:

* Field type specific parameters - like how it's going to be searched for text fields.
* Global parameters - is this field filterable, facetable and sortable.

Go to [the mapping reference](../reference/config/mapping.md) section for more details on all parameters.
# Nixiesearch JSON document format

Nixiesearch does not have a strict incoming JSON document schema: any format is probably OK while it can be processed using the existing [index mapping](mapping.md).

## Document identifier

Each Nixiesearch-indexed document is expected (but not required) to have a global identifier. This identifier is useful for later document manipulation like update and removal:

* if JSON document has a special `_id` field defined, then it is used as an identifier.
* if the `_id` field is missing in the document payload, then a UUID-based random identifier is generated automatically.

Internally the document id is a sequence of bytes, and any real JSON type of the `_id` field (like string and number) will be automatically mapped to the internal id representation.

The `_id` field is automatically added to every index with `type: id`, which is a special field type optimized for document identifiers. This field type has the following characteristics:

* `store: true` - document ID is always stored
* `filter: true` - can be used in filter queries
* `facet: false` - cannot be used for faceting
* `sort: false` - not sortable by default

You don't need to define the `_id` field in your schema - it's added automatically.

This is an exampe of a good `_id` field:

```json
{"_id": 10, "title": "hello"}
```

This is also a good `_id`:
```json
{"_id": "levis-jeans-1937481", "title": "jeans" }
```

## Flat documents

Flat documents without any nesting are mapped 1-to-1 to underlying index fields. For example:

```json
{
  "_id": 1,
  "title": "socks",
  "price": 10.5
}
```

will match the following index mapping:

```yaml
schema:
  my-index:
    fields:
      - title:
          type: text
      - price:
          type: float
```

## Repeated fields

Compared to Elasticsearch/Solr, Nixiesearch has a distinction between repeated and singular fields:

* based on a field type, more specific and optimized data structures can be used.
* when returning a stored field, a JSON response field types can follow the index mapping. In other words, not all returned fields are arrays (like in Elasticsearch), and only the ones being defined as repeated.

As for version `0.6.0` Nixiesearch only supports repeated [textual fields](types/text.md).

For example, the following document:

```json
{"_id": 1, "tags": ["a", "b", "c"]}
```

should match the following index mapping:

```yaml
schema:
  my-index:
    fields:
      - tags:
          type: text[]
```

## Nested documents

Nixiesearch maps your documents into an underlying Lucene document representation, which is internally just a flat list of fields. To handle nesting, all the rich document structure is flattened.

A non-repeated nested document like this:

```json
{
  "_id": 1,
  "meta": {
    "asin": "AAA123"
  }
}
```

will be flattened into a dot-separated field in the mapping:

```yaml
schema:
  my-index:
    fields:
      - meta.asin:
          type: text
```

When indexing documents, nested JSON objects are automatically flattened using dot notation. Both flat and nested formats work with the same mapping:

```json
// Flat format
{"_id": 1, "meta.asin": "AAA123"}

// Nested format (automatically flattened)
{"_id": 1, "meta": {"asin": "AAA123"}}
```

Repeated documents are also flattened in a similar way, but with a notable exception of transforming all internal singular fields into repeated ones:

```json
{
  "_id": 1,
  "tracks": [
    {"name": "yellow submarine"},
    {"name": "smells like teen spirit"}
  ]
}
```

will flatten itself into a collection of repeated fields:

```yaml
schema:
  my-index:
    fields:
      - tracks.name:
          type: text[]

```

## Pre-embedded text fields

By default, for [text fields](types/text.md) Nixiesearch expects a `JSON string` (or array of strings for `text[]`) as a document field type, and handles the embedding process internally. Consider the following index schema:

```yaml
inference:
  embedding:
    my-model:
      model: intfloat/e5-small-v2
schema:
  my-index:
    fields:
      title:
        type: text
        search:
          semantic:
            model: my-model
      tags:
        type: text[]
        search:
          semantic:
            model: my-model
```

For a JSON document with server-side inference:

```json
{
  "title": "cookies",
  "tags": ["scala", "functional", "programming"]
}
```

Nixiesearch will run embedding inference for the model `intfloat/e5-small-v2`. When you already have text embeddings for documents, you can skip the inference process by providing field text and embedding at the same time:

### text fields

```json
{"title": {"text": "cookies", "embedding": [0.1, 0.2, 0.3, ...]}}
```

### text[] fields

For `text[]` fields, you can provide embeddings in multiple formats:

**1:1 text-to-embedding mapping:**
```json
{
  "tags": {
    "text": ["scala", "functional", "programming"],
    "embedding": [
      [0.1, 0.2, 0.3, ...],  // embedding for "scala"
      [0.4, 0.5, 0.6, ...],  // embedding for "functional"
      [0.7, 0.8, 0.9, ...]   // embedding for "programming"
    ]
  }
}
```

**Multiple embeddings for a single text:**

A single text value can have multiple embeddings. This is useful for multi-perspective embeddings or chunk-based approaches:

```json
{
  "description": {
    "text": ["product overview"],
    "embedding": [
      [0.1, 0.2, 0.3, ...],  // embedding perspective 1
      [0.4, 0.5, 0.6, ...],  // embedding perspective 2
      [0.7, 0.8, 0.9, ...]   // embedding perspective 3
    ]
  }
}
```

**Summary of ingestion formats:**

* `JSON string` (for `text`) or `JSON array` (for `text[]`): when embedding inference is handled by the server
* `JSON obj` with `text` and `embedding` fields: when embedding inference is skipped
  * For `text`: `embedding` is a single array of floats
  * For `text[]`: `embedding` is an array of arrays with the following constraints:
    - **1:1 mapping**: number of embeddings equals number of text values
    - **1:N mapping**: a single text value with N embeddings (useful for multi-perspective or chunk-based representations)

**Note**: During search with `text[]` fields, all embeddings are considered, and the highest-scoring embedding determines the document's relevance score. See [multi-vector search for text[] fields](types/text.md#multi-vector-search-for-text-fields) for details.
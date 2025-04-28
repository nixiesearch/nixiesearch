# Nixiesearch JSON document format

Nixiesearch does not have a strict incoming JSON document schema: any format is probably OK while it can be processed using the existing [index mapping](mapping.md).

## Document identifier

Each Nixiesearch-indexed document is expected (but not required) to have a global identifier. This identifier is useful for later document manipulation like update and removal:

* if JSON document has a special `_id` field defined, then it is used as an identifier.
* if the `_id` field is missing in the document payload, then a UUID-based random identifier is generated automatically.
 
Internally the document id is a sequence of bytes, and any real JSON type of the `_id` field (like string and number) will be automatically mapped to the internal id representation.

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

By default, for [text fields](types/text.md) Nixiesearch expends a `JSON string` as a document field type, and handles the embedding process internally. Consider the following index schema:

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
```

So for a JSON document of:

```json
{"title": "cookies"}
```

Nixiesearch will run an embedding inference for a model `intfloat/e5-small-v2`. In a case when you already have text embeddings for documents, you can skip the inference process altogether by providing field text and embedding at the same time:

```json
{"title": {"text": "cookies", "embedding": [1,2,3]}}
```

So a text field has two possible ingestion formats:

* `JSON string`: when embedding inference handled by the server
* `JSON obj` with two fields of `text: string` and `embedding: array[float]` when embedding inference is skipped.
# Numeric fields

Numeric fields `int`, `float`, `long`, `double`, `bool` are supported. The `bool` field is more an API syntax sugar and is built on top of an internal `int` field.

Numeric fields can be [filtered](../../search/filter.md#filters), [sorted by](../../search/sort.md) and [aggregated](../../search/facet.md).

## Single-value numeric fields

Example field schema for a numeric field `year`:

```yaml
schema:
  movies:
    fields:
      title:
        type: text
        search: false
      year:
        type: int       # there can be single year
        filter: true    # field is filterable, default false
        facet: true     # field is facetable, default false
        store: true     # field is stored, default true
        sort: true     # field is sortable, default false
```

## Numeric list fields

Nixiesearch also supports array/list variants of numeric fields: `int[]`, `long[]`, `float[]`, `double[]`. These allow storing multiple numeric values for a single field.

```yaml
schema:
  products:
    fields:
      title:
        type: text
      ratings:
        type: int[]     # array of user ratings
        filter: true    # enable range filtering on ratings
      sizes:
        type: float[]   # available product sizes
        filter: true
        store: true
      dimensions:
        type: double[]  # product dimensions [length, width, height]
        store: true
```

### JSON document format

When indexing documents with numeric list fields, use standard JSON arrays:

```json
{
  "title": "Wireless Headphones",
  "ratings": [5, 4, 5, 3, 4, 5],
  "sizes": [6.5, 7.0, 7.5, 8.0, 8.5],
  "dimensions": [15.2, 18.5, 7.3]
}
```

### Use cases

Numeric list fields are ideal for:

- **Product attributes**: Multiple sizes, available ratings, variant prices
- **Multi-dimensional data**: Product dimensions, feature vectors
- **Category scores**: Relevance scores across different categories
- **Multiple values**: Any case where a document has multiple numeric values for the same attribute

### Configuration options

| Field Type | Single | Array | Description |
|------------|--------|-------|-------------|
| `int`      | `int`  | `int[]`    | 32-bit integers |
| `long`     | `long` | `long[]`   | 64-bit integers |
| `float`    | `float`| `float[]`  | 32-bit floating point |
| `double`   | `double`| `double[]` | 64-bit floating point |

All numeric list fields support:
- `store: true/false` - whether to store the original values (default: true)
- `filter: true/false` - whether the field can be filtered (default: false)
- `required: true/false` - whether the field is required (default: false)

**Note**: Numeric array fields (`int[]`, `long[]`, `float[]`, `double[]`) do **not** support sorting operations. Only single-value numeric fields can be sorted.

## Search behavior

Numeric fields cannot be searched in the sense of full text search as [text](text.md) fields do, but you can [filter](../../search/filter.md) over them.

For numeric list fields, filtering operations check if **any** element in the array matches the criteria. For example, filtering `ratings >= 4` on `[5, 4, 5, 3, 4, 5]` would match because several elements are >= 4.

By default all numeric fields are only stored, for `filter`, `sort` and `facet` support you need to explicitly toggle the corresponding flag.

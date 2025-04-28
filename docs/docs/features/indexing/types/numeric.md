# Numeric fields

Numeric fields `int`, `float`, `long`, `double`, `bool` are supported. The `bool` field is more an API syntax sugar and is built on top of an internal `int` field.

Numeric fields can be [filtered](../../search/filter.md#filters), [sorted by](../../search/sort.md) and [aggregated](../../search/facet.md).

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

Numeric fields cannot be searched in the sense of full text search as [text](text.md) fields do, but you can [filter](../../search/filter.md) over them.

By default all numeric fields are only stored, for `filter`, `sort` and `facet` support you need to explicitly toggle the corresponding flag.

# Field types

Nixiesearch supports following field types:

* [text fields](text.md): `text`, `text[]`.
* [numeric fields](numeric.md): `int`, `float`, `long`, `double`, `bool`.
* [geolocation fields](geo.md): `geopoint`
* [date fields](date.md): `date`, `datetime`

Other field types like `int[]`, `float[]` are not yet supported - but are on the roadmap (see [issue #541](https://github.com/nixiesearch/nixiesearch/issues/541) for more details).

## Setting field type

In the field definition in the [index mapping](../mapping.md) the required field `type` defines the underlying data type used to store and process the field.

For example, defining a `text` field looks like this:

```yaml
schema:
  movies:
    fields:
      title:
        type: text # <-- the field type is here
        search: 
          semantic:
            model: e5-small

```

Field type should be defined in advance before you start indexing documents, as it affects:

* the way field is stored on disk (or not stored at all, if you set `stored: false`).
* underlying index structures if you plan to `search`, `filter`, `facet` and `sort` over this field.
* document schema check during ingestion. 

For further reading, see [text](text.md), [numeric](numeric.md), [date](date.md) and [geo](geo.md) fields sections.



# Field types

Nixiesearch supports following field types:

* [text fields](#text-fields): `text`, `text[]`.
* [numeric fields](#numeric-fields): `int`, `float`, `long`, `double`, `bool`.
* other fields: `bool`
* geolocation fields: `geopoint`
* date fields: `date`, `datetime`

Other field types like `int[]`, `float[]` are not yet supported - but are on the roadmap.

## Text fields

Unlike other Lucene-based search engines, Nixiesearch has [a distinction between singular and repeated](../format.md#repeated-fields) fields on a [schema](../mapping.md) level - so choose your field type wisely.

Example field schema for a text fields `title` and `genre`:

```yaml
schema:
  movies:
    fields:
      title:
        type: text      # only a single title is allowed
        search: 
          type: semantic
          model: e5-small
      genre:
        type: text[]    # there can be multiple genres
        search: 
          type: semantic
          model: e5-small
        filter: true    # field is filterable
        facet: true     # field is facetable
```

## Numeric fields

Numeric fields `int`, `float`, `long`, `double`, `bool` are supported. The `bool` field is more an API syntax sugar and is built on top of an internal `int` field. 

Numeric fields can be [filtered](../../search/filter.md#filters) and [aggregated](../../search/facet.md).

Example field schema for a numeric field `year`:

```yaml
schema:
  movies:
    fields:
      title:
        type: text      # only a single title is allowed
        search: 
          type: semantic
          model: e5-small
      year:
        type: int       # there can be single year
        filter: true    # field is filterable
        facet: true     # field is facetable
```

## Geo fields

`geopoint` fields are defined by their lat/lon coordinates:

```json
{
  "_id": 1,
  "name": "KFC",
  "location": {"lat": 1.0, "lon": 2.0}
}
```

You can perform `geo_distance` and `geo_box` [filters](../../search/filter.md#geolocation-filters) over `geopoint` fields.

Example field schema for a `geopoint` field `location`:

```yaml
schema:
  movies:
    fields:
      title:
        type: text      # only a single title is allowed
        search: 
          type: semantic
          model: e5-small
      location:
        type: geopoint  
        filter: true    # field is filterable
                        # note that geopoint fields are not facetable
```


## Date and Datetime fields

`Date` fields are strings in an [ISO 8601](https://en.wikipedia.org/wiki/ISO_8601) format:

```json
{
  "_id": 1,
  "name": "KFC",
  "date": "2024-01-01"
}
```

Internally dates are stored as integer fields with a day offset from the start of the epoch (`1970-01-01`).

Like `date` fields, `datetime` fields are also strings in an [ISO 8601](https://en.wikipedia.org/wiki/ISO_8601):

```json
{
  "_id": 1,
  "name": "KFC",
  "date": "2024-01-01T00:00:01Z"
}
```

`datetime` fields are stored as milliseconds till the UNIX epoch in the UTC timezone:

* Nixiesearch implicitly converts non-UTC `datetime` fields into the UTC zone
* Only the UTC-zoned `datetime` fields are returned. You need to perform the timezone conversion on the application side.

`date` and `datetime` fields can be [filtered](../../search/filter.md#filters) and [aggregated](../../search/facet.md).

Example field schema for a datetime field `updated_at`:

```yaml
schema:
  movies:
    fields:
      title:
        type: text      # only a single title is allowed
        search: 
          type: semantic
          model: e5-small
      updated_at:
        type: datetime  
        filter: true    # field is filterable
        facet: true     # field is facetable
```

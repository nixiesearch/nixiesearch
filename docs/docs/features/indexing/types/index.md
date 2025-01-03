# Field types

Nixiesearch supports following field types:

* [text fields](#text-fields): `text`, `text[]`.
* [numeric fields](#numeric-fields): `int`, `float`, `long`, `double`, `bool`.
* other fields: `bool`
* geolocation fields: `geopoint`

Other field types like `int[]`, `float[]`, `date` and `datetime` are not yet supported - but are on the roadmap.

## Text fields

Unlike other Lucene-based search engines, Nixiesearch has [a distinction between singular and repeated](../format.md#repeated-fields) fields on a [schema](../mapping.md) level - so choose your field type wisely.

## Numeric fields

Numeric fields `int`, `float`, `long`, `double`, `bool` are supported. The `bool` field is more an API syntax sugar and is built on top of an internal `int` field. 

## Geo fields

Geopoint fields are defined by their lat/lon coordinates:

```json
{
  "_id": 1,
  "name": "KFC",
  "location": {"lat": 1.0, "lon": 2.0}
}
```
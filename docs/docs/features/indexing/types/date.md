# Date fields

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
        type: text
        search: false
      updated_at:
        type: datetime  
        filter: true    # field is filterable
        facet: true     # field is facetable
```

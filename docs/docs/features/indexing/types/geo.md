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

It is also possible to [do a distance sort](../../search/sort.md#distance-sorting) for this field type.

Example field schema for a `geopoint` field `location`:

```yaml
schema:
  movies:
    fields:
      title:
        type: text   
        search: false
      location:
        type: geopoint  
        filter: true    # field is filterable
                        # note that geopoint fields are not facetable
```


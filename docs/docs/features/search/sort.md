# Sorting search results

You can sort search results based on one or more sort predicates on fields marked as sortable (e.g. having a `sort: true` option in the [index mapping](../indexing/mapping.md#mapping-options)). You can also reverse the sorting order, sort by document `_score` and also by document indexing order using field `_doc`.

As sorting by a specific field requires holding an in-memory [DocValue data structure](https://lucene.apache.org/core/10_0_0/core/org/apache/lucene/index/DocValues.html), by default all fields are not sortable. To make field sortable, add the `sort: true` option in the field configuration:

```yaml
schema:
  <index-name>:
    fields:
      price:
        type: double
        sort: true # by default all fields are non-sortable
      created_at:
        type: datetime
        sort: true
      category:
        type: text
        search: false
        sort: true
```

For the [index mapping](../indexing/mapping.md#mapping-options) above you can use the following search request to sort by field values:

```shell
curl -XPOST /<index-name>/_search \
    -d '{
          "query": {"match_all": {}},
          "sort": [
            "price",
            {"created_at": {"order": "asc"}},
            {"category": {"missing": "last"}}
          ]
        }'
```

A sorting predicate can be defined in two forms:

* **short**: just a field name, with all the default options.
  Example:
```json
{
  "sort": ["price", "color"]
}
```
* **full**: a JSON object with non-default options:
```json
{
  "sort": [
    {
      "price": {
        "order": "asc",
        "missing": "last"
      }
    }
  ]
}
```

Sorting can be done over [numeric](#sorting-numeric-fields), [text](#sorting-text-fields) and [geopoint](#distance-sorting) fields.

## Sort order

For any non distance sorting predicate, the `order` option can have the following values:

* `desc`: sort in descending order. Default value for `_score` field.
* `asc`: sort in ascending order. Default value for other fields.

Distance sorting can only be done in the ascending order.

## Missing values

In a case when a document field is not marked as `required: true` (Work-in-progress feature, see [PR#482](https://github.com/nixiesearch/nixiesearch/issues/482) for details), the position of such documents can be controlled with the `missing` option having two possible values:

* `{"missing": "last"}` - the default behavior, all documents with missing field value are on the lowest position.
* `{"missing": "first"}` - so all the documents without field are on the top.

## Sorting numeric fields

Nixiesearch supports sorting over all numerical field types like `int`, `float`, `long` and `double`. Other semi-numeric field types can also be sorted:

* `date` field is internally stored as a number of days from Unix epoch (`1970-01-01`), so it behaves as a regular `int` field.
* `datetime` field is stored as millis from the start of epoch, so is semantically equals to a `long` field.
* `boolean` field is mapped to `1` and `0` and also behaves as `int` field.

## Sorting text fields

When a text field is marked as sorted, it is sorted lexicographically without any internal analysis and processing. The same field can be searchable and sortable, as internally these are two separate Lucene fields with different analysis pipelines:

* `search: true` marked field uses a regular language specific analysis (e.g. ICU tokenization, stopwords removal, etc.)
* `sort: true` field also creates an internal `<field-name>$sort` field with no analysis, so these two fields do not interfere.

## Distance sorting

For `geopoint` fields it is possible to sort by distance from a specific point. With the following schema:

```yaml
schema:
  <index-name>:
    fields:
      location:
        type: geopoint
        sort: true # by default all fields are non-sortable
```

To sort documents by distance the following request can be used:

```shell
curl -XPOST /<index-name>/_search \
    -d '{
          "query": {"match_all": {}},
          "sort": [
            {
              "location": { 
                "lat": 12.34,
                "lon": 56.78 
              }
            }
          ]
        }'
```

Distance sorting can only be done in the `asc` order (so from starting from the closest document), and missing values are always `last`.
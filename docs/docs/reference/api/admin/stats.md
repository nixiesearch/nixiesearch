# Index stats

A system REST API call to `/<index-name>/_stats` can be used to get internal statistics over an index.

## Usage

With cURL:

```shell
$> curl http://localhost:8080/<index-name>/_stats
{
  "luceneVersion": "9.10.0",
  "segments": [
    {
      "name": "_0",
      "maxDoc": 64,
      "codec": "Lucene99",
      "files": [
        "_0.cfe",
        "_0.si",
        "_0.cfs"
      ],
      "delCount": 0
    }
  ],
  "leaves": [
    {
      "docBase": 0,
      "ord": 0,
      "numDocs": 64,
      "numDeletedDocs": 0,
      "fields": [
        {
          "name": "_id",
          "number": 0,
          "storePayloads": false,
          "indexOptions": "NONE",
          "attributes": {},
          "vectorDimension": 0,
          "vectorEncoding": "FLOAT32",
          "vectorSimilarityFunction": "EUCLIDEAN"
        },
        {
          "name": "_id$raw",
          "number": 1,
          "storePayloads": false,
          "indexOptions": "DOCS",
          "attributes": {
            "PerFieldPostingsFormat.format": "Lucene99",
            "PerFieldPostingsFormat.suffix": "0"
          },
          "vectorDimension": 0,
          "vectorEncoding": "FLOAT32",
          "vectorSimilarityFunction": "EUCLIDEAN"
        },
        {
          "name": "title",
          "number": 2,
          "storePayloads": false,
          "indexOptions": "NONE",
          "attributes": {
            "PerFieldKnnVectorsFormat.format": "Lucene99HnswVectorsFormat",
            "PerFieldKnnVectorsFormat.suffix": "0"
          },
          "vectorDimension": 768,
          "vectorEncoding": "FLOAT32",
          "vectorSimilarityFunction": "COSINE"
        },
        {
          "name": "overview",
          "number": 3,
          "storePayloads": false,
          "indexOptions": "NONE",
          "attributes": {
            "PerFieldKnnVectorsFormat.format": "Lucene99HnswVectorsFormat",
            "PerFieldKnnVectorsFormat.suffix": "0"
          },
          "vectorDimension": 768,
          "vectorEncoding": "FLOAT32",
          "vectorSimilarityFunction": "COSINE"
        }
      ]
    }
  ]
}
```

## Response format

The `/_stats` API response is not yet fixed and might be changed in the future.
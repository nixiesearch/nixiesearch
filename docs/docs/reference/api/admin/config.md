# _config route

Used to get running config for a Nixiesearch instance. As for pre 1.0 versions of Nixiesearch, the JSON schema of the `_config` endpoint output is not defined, so there can be backwards-incompatible changes.

An example:

```shell
$ curl http://localhost:8080/_config

{
  "searcher" : {
    "host" : "0.0.0.0",
    "port" : 8080
  },
  "indexer" : {
    
  },
  "core" : {
    
  },
  "schema" : {
    "movies" : {
      "name" : "movies",
      "alias" : [
      ],
      "config" : {
        "mapping" : {
          "dynamic" : false
        }
      },
      "store" : {
        "type" : "local",
        "local" : {
          "type" : "disk",
          "path" : "/home/nixiesearch/indexes"
        }
      },
      "cache" : {
        "embedding" : {
          "maxSize" : 32768
        }
      },
      "fields" : {
        "popularity" : {
          "type" : "double",
          "name" : "popularity",
          "store" : true,
          "sort" : false,
          "facet" : true,
          "filter" : true
        },
      }
    }
  }
}
```
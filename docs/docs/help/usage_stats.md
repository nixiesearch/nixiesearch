# Anonymous Usage Statistics

The Nixiesearch open-source container image collects **anonymous usage statistics** to help us improve the engine. You can turn this off anytime, and if you'd like us to delete any data that's already been collected, just [let us know](contact.md).

## Why do we even collect usage data?

Our goal is to make Nixiesearch as fast, reliable, and efficient as possible. While we do a ton of testing on our side, there’s no substitute for real-world data. Everyone runs Nixiesearch differently—with different hardware, configurations, and workloads—so your usage helps us spot edge cases, performance bottlenecks, and bugs that we might never catch on our own.

We also use internal heuristics to fine-tune performance, and telemetry helps us understand how well those optimizations are working. In short: this data helps us make Nixiesearch better for everyone.

## What gets collected

Here’s what we do collect:

* **System Info** – CPU type, RAM size, and how your Nixiesearch instance is configured.
* **Performance Metrics** – Timings and counters from key parts of the codebase.
* **Critical Error Reports** – Stack traces and details about serious crashes or bugs that haven’t been reported yet.

Here’s what we don’t collect:

* Your IP address and your location
* Any identifying info about you or your organization
* The actual data stored in your collections
* Names, fields and URLs in the configuration

## How We Anonymize Data

We take your privacy seriously. Here’s how we make sure collected data stays anonymous:

* We hash all names, so things like field and index names are turned into random-looking strings.
* URLs are hashed too: nothing identifiable is stored.

An example of usage telemetry payload:

```json
{
  "config": {
    "inference": {
      "embedding": {
        "ca1930b673f7fa40deb02c0f42401488": {
          "model": "nixiesearch/e5-small-v2-onnx",
          "pooling": "mean",
          "prompt": {
            "doc": "passage: ",
            "query": "query: "
          },
          "file": null,
          "normalize": true,
          "maxTokens": 512,
          "batchSize": 32,
          "cache": {
            "inmem": {
              "maxSize": 32768
            }
          },
          "provider": "onnx"
        }
      },
      "completion": {}
    },
    "searcher": {},
    "indexer": {},
    "core": {
      "cache": {
        "dir": "353eb9e18184df37da5dc0222f2a1b2f"
      },
      "host": "0.0.0.0",
      "port": 8080,
      "loglevel": "info"
    },
    "schema": {
      "55ba44c548d3ebafd9f70e64a7f232b0": {
        "name": "55ba44c548d3ebafd9f70e64a7f232b0",
        "alias": [],
        "config": {
          "mapping": {
            "dynamic": false
          },
          "flush": {
            "interval": "5s"
          },
          "hnsw": {
            "m": 16,
            "efc": 100,
            "workers": 16
          }
        },
        "store": {
          "type": "local",
          "local": {
            "type": "disk",
            "path": "55ba44c548d3ebafd9f70e64a7f232b0"
          }
        },
        "fields": {
          "84cdc76cabf41bd7c961f6ab12f117d8": {
            "type": "int",
            "name": "84cdc76cabf41bd7c961f6ab12f117d8",
            "store": true,
            "sort": false,
            "facet": true,
            "filter": true
          }
        }
      }
    }
  },
  "system": {
    "os": "Linux",
    "arch": "amd64",
    "jvm": "21",
    "args": "-Xmx1g -verbose:gc --add-modules=jdk.incubator.vector -Dorg.apache.lucene.store.MMapDirectory.enableMemorySegments=false -XX:-OmitStackTraceInFastThrow -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
  },
  "confHash": "59ecb6ff79f8b2309216cef88226960d",
  "macHash": "84abbc15cdc0f5389cc24b2a56fd267313ca81a618173c8f36e0fcef4c9b68a2",
  "version": "0.4.1",
  "mode": "standalone"
}
```

Want to see exactly what we collect? You can! Just hit the telemetry API like this:

```shell
curl -XGET http://localhost:8080/v1/system/telemetry
```

## How to disable telemetry

You can turn off usage data collection in a few ways:

* Set the `NIXIESEARCH_CORE_TELEMETRY` [environment variable](../reference/config.md#environment-variables-overrides) to `false`
* Set `core.telemetry: false` in your [config file](../reference/config.md)

Any of these will stop Nixiesearch from sending telemetry.

If you do disable it, we’d love to hear your thoughts on why - Feel free to share feedback in [our slack](../help/contact.md)!

## Want Your Data Deleted?

No problem - just [contact us](contact.md) at with the unique identifier for your Nixiesearch instance. You’ll find that ID in the telemetry API response (look for the "macHash" field).

You can also use that address for any other questions or concerns about the data we collect.
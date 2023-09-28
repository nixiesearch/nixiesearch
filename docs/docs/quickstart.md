# Quickstart

This guide shows how to install Nixiesearch on a single machine using Docker. We will run the service in a [standalone](reference/cli/standalone.md) mode, [index](concepts/index.md) a corpus of documents and run a couple of [search](concepts/search.md) queries.

## Prerequisites

This guide assumes that you already have the following available:

* Docker: [Docker Desktop](https://docs.docker.com/engine/install/) for Mac/Windows, or Docker for Linux.
* Operating system: Linux, macOS, Windows with [WSL2](https://learn.microsoft.com/en-us/windows/wsl/install).
* Architecture: x86_64. On Mac M1+, you should be able to run x86_64 docker images on arm64 Macs with [Rosetta](https://levelup.gitconnected.com/docker-on-apple-silicon-mac-how-to-run-x86-containers-with-rosetta-2-4a679913a0d5).
* Memory: 2Gb dedicated to Docker.

## Getting the dataset

For this quickstart we will use a sample of the [MSMARCO](https://microsoft.github.io/msmarco/) dataset, which contains text documents from the [Bing](https://www.bing.com/) search engine. The following command will fetch the sample data to your current directory:
```
$ curl -L -O http://nixiesearch.ai/data/msmarco.json

  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100   162  100   162    0     0   3636      0 --:--:-- --:--:-- --:--:--  3681
100 32085  100 32085    0     0   226k      0 --:--:-- --:--:-- --:--:--  226k

```

Data format is JSONL, where each line is a separate json object - and there are only two fields inside:

* `_id` - an optional document identifier.
* `text` - the textual payload we're going to search through.

```json lines
{"_id":"2637788","text":"2 things, one is ethyol alcohol, and the other is CO2."}
{"_id":"2815157","text":"Things I wish I had known before I became an academic."}
{"_id":"2947247","text":"Not to be confused with Auburn Township, Pennsylvania."}
```

## Starting the service

Nixiesearch is distributed as a Docker container, which can be run with the following command:
```
$ docker run -i -t -p 8080:8080 nixiesearch/nixiesearch:latest standalone

12:40:47.325 INFO  ai.nixiesearch.main.Main$ - Staring Nixiesearch
12:40:47.460 INFO  ai.nixiesearch.config.Config$ - No config file given, using defaults
12:40:47.466 INFO  ai.nixiesearch.config.Config$ - Store: LocalStoreConfig(LocalStoreUrl(/))
12:40:47.557 INFO  ai.nixiesearch.index.IndexRegistry$ - Index registry initialized: 0 indices, config: LocalStoreConfig(LocalStoreUrl(/))
12:40:48.253 INFO  o.h.blaze.server.BlazeServerBuilder - 
███╗   ██╗██╗██╗  ██╗██╗███████╗███████╗███████╗ █████╗ ██████╗  ██████╗██╗  ██╗
████╗  ██║██║╚██╗██╔╝██║██╔════╝██╔════╝██╔════╝██╔══██╗██╔══██╗██╔════╝██║  ██║
██╔██╗ ██║██║ ╚███╔╝ ██║█████╗  ███████╗█████╗  ███████║██████╔╝██║     ███████║
██║╚██╗██║██║ ██╔██╗ ██║██╔══╝  ╚════██║██╔══╝  ██╔══██║██╔══██╗██║     ██╔══██║
██║ ╚████║██║██╔╝ ██╗██║███████╗███████║███████╗██║  ██║██║  ██║╚██████╗██║  ██║
╚═╝  ╚═══╝╚═╝╚═╝  ╚═╝╚═╝╚══════╝╚══════╝╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝╚═╝  ╚═╝
                                                                               
12:40:48.267 INFO  o.h.blaze.server.BlazeServerBuilder - http4s v1.0.0-M38 on blaze v1.0.0-M38 started at http://0.0.0.0:8080/
```

Options breakdown:

* `-i` and `-t`: interactive docker mode with allocated TTY. Useful when you want to be able to press Ctrl-C to stop the application.
* `-p 8080:8080`: expose the port 8080.
* `standalone`: a Nixiesearch running mode, with colocated indexer and searcher processes.

## Indexing data

After you start the Nixiesearch service in the `standalone` mode listening on port `8080`, let's index some docs!

> **Note**: Nixiesearch does not require you to have an explicitly defined index schema and can generate it on the fly. 
>
> In this quickstart guide we will skip creating explicit index mapping, but it is always a good idea to have it prior to indexing. See [Mapping](reference/config/mapping.md) section for more details. 

Nixiesearch uses a similar API semantics as Elasticsearch, so to upload docs for indexing, you need to make a HTTP PUT request to the `/<index-name>/_index` endpoint:

```
$ curl -XPUT -d @msmarco.json http://localhost:8080/msmarco/_index

{"result":"created","took":8256}
```

As Nixiesearch is running an LLM embedding model inference inside, indexing large document corpus on CPU may take a while.

## Sending requests


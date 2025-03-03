# Indexing

Nixiesearch supports two ways of running indexing:

* **Push-based**: a traditional REST API endpoint where you post documents to, like done in Elastic and Solr. Easy to start with, hard to scale due to backpressure and consistency issues.
* **Pull-based**: when Nixie pulls data from remote endpoint, maintaining the position and ingestion throughput.

Pull-based indexing can be done over both local, HTTP and S3-hosted files, see [Supported URL locations](../url.md) for more details.

Nixie index CLI subcommand has the following options:

```shell
$ java -jar target/scala-3.4.2/nixiesearch.jar index --help

13:49:47.757 INFO  ai.nixiesearch.main.Main$ - Staring Nixiesearch
13:49:47.833 INFO  ai.nixiesearch.main.CliConfig - 
  -h, --help   Show help message

Subcommand: api
  -c, --config  <arg>     Path to a config file
  -h, --host  <arg>       iface to bind to, optional, default=0.0.0.0
  -l, --loglevel  <arg>   Logging level: debug/info/warn/error, default=info
  -p, --port  <arg>       port to bind to, optional, default=8080
      --help              Show help message

Subcommand: file
  -c, --config  <arg>     Path to a config file
  -e, --endpoint  <arg>   custom S3 endpoint, optional, default=None
  -i, --index  <arg>      to which index to write to
  -l, --loglevel  <arg>   Logging level: debug/info/warn/error, default=info
  -r, --recursive         recursive listing for directories, optional,
                          default=false
  -u, --url  <arg>        path to documents source
  -h, --help              Show help message

Subcommand: kafka
  -b, --brokers  <arg>    Kafka brokers endpoints, comma-separated list
  -c, --config  <arg>     Path to a config file
  -g, --group_id  <arg>   groupId identifier of consumer. default=nixiesearch
  -i, --index  <arg>      to which index to write to
  -l, --loglevel  <arg>   Logging level: debug/info/warn/error, default=info
  -o, --offset  <arg>     which topic offset to use for initial connection?
                          earliest/latest/ts=<unixtime>/last=<offset>
                          default=none (use committed offsets)
      --options  <arg>    comma-separated list of kafka client custom options
  -t, --topic  <arg>      Kafka topic name
  -h, --help              Show help message
java.lang.Exception: No command given. If unsure, try 'nixiesearch standalone'
```

## Offline indexing

Primary offline indexing use-case is batch and full-reindex jobs:

* when you changed index mapping by adding/altering a field and need to re-process the whole document corpus.
* when performing major non-backwards compatible Nixiesearch upgrades.

Nixiesearch can load a [JSONL document format](../../features/indexing/format.md) from a [local, HTTP or S3 hosted file or directory](../url.md).

!!! note 

    Make sure that field names in your source documents and in index mapping match! Nixiesearch will normally ignore non-mapped fields, but incompatible field formats (e.g. in JSON it's string, but in mapping it's an integer) will result in an error. 

To run an offline indexing job, use the `index file` subcommand:

```shell
docker run -i -t -v <your-local-dir>:/data nixiesearch/nixiesearch:latest index file\
  --config /data/conf.yml --index <index name> --url file:///data/docs.json
```

Nixiesearch will report indexing progress in logs:

```
$ docker run -i -t nixiesearch/nixiesearch:latest index file -v <dir>/data --config /data/config.yaml --index movies --url /data/movies.jsonl
 
14:05:04.074 INFO  ai.nixiesearch.main.Main$ - Staring Nixiesearch
14:05:04.121 INFO  a.n.main.subcommands.IndexMode$ - Starting in 'index' mode with indexer only 
14:05:04.246 INFO  ai.nixiesearch.config.Config$ - Loaded config: /home/shutty/tmp/nixie-experiments/config.yaml
14:05:04.249 INFO  a.n.index.sync.LocalDirectory$ - initialized MMapDirectory
14:05:04.251 INFO  a.n.index.sync.LocalDirectory$ - created on-disk directory /home/shutty/tmp/nixie-experiments/indexes/movies
May 24, 2024 2:05:04 PM org.apache.lucene.store.MemorySegmentIndexInputProvider <init>
INFO: Using MemorySegmentIndexInput with Java 21 or later; to disable start with -Dorg.apache.lucene.store.MMapDirectory.enableMemorySegments=false
14:05:04.268 INFO  a.nixiesearch.index.sync.LocalIndex$ - index dir does not contain manifest, creating...
14:05:04.319 INFO  a.n.core.nn.model.BiEncoderCache$ - loading ONNX model hf://nixiesearch/e5-small-v2-onnx
14:05:04.325 INFO  a.n.core.nn.model.ModelFileCache$ - using /home/shutty/.cache/nixiesearch as local cache dir
14:05:04.416 WARN  o.h.ember.client.EmberClientBuilder - timeout (120 seconds) is >= idleConnectionTime (60 seconds). It is recommended to configure timeout < idleConnectionTime, or disable one of them explicitly by setting it to Duration.Inf.
14:05:04.624 INFO  a.n.core.nn.model.HuggingFaceClient - found cached model.json card
14:05:04.625 INFO  a.n.c.n.m.l.HuggingFaceModelLoader$ - loading model_quantized.onnx
14:05:04.625 INFO  a.n.c.n.m.l.HuggingFaceModelLoader$ - Fetching hf://nixiesearch/e5-small-v2-onnx from HF: model=model_quantized.onnx tokenizer=tokenizer.json
14:05:04.726 INFO  a.n.core.nn.model.HuggingFaceClient - found model_quantized.onnx in cache
14:05:04.729 INFO  a.n.core.nn.model.HuggingFaceClient - found tokenizer.json in cache
14:05:04.730 INFO  a.n.core.nn.model.HuggingFaceClient - found config.json in cache
14:05:08.760 INFO  ai.djl.util.Platform - Found matching platform from: jar:file:/home/shutty/tmp/nixie-experiments/nixiesearch.jar!/native/lib/tokenizers.properties
14:05:08.793 WARN  a.d.h.t.HuggingFaceTokenizer - maxLength is not explicitly specified, use modelMaxLength: 512
14:05:08.919 INFO  a.n.core.nn.model.OnnxSession$ - Loaded ONNX model (size=32 MB inputs=List(input_ids, attention_mask, token_type_ids) outputs=List(last_hidden_state) dim=384)
14:05:08.928 INFO  a.n.core.nn.model.BiEncoderCache$ - loading ONNX model hf://nixiesearch/e5-small-v2-onnx
14:05:08.928 INFO  a.n.core.nn.model.ModelFileCache$ - using /home/shutty/.cache/nixiesearch as local cache dir
14:05:08.929 WARN  o.h.ember.client.EmberClientBuilder - timeout (120 seconds) is >= idleConnectionTime (60 seconds). It is recommended to configure timeout < idleConnectionTime, or disable one of them explicitly by setting it to Duration.Inf.
14:05:08.929 INFO  a.n.core.nn.model.HuggingFaceClient - found cached model.json card
14:05:08.929 INFO  a.n.c.n.m.l.HuggingFaceModelLoader$ - loading model_quantized.onnx
14:05:08.929 INFO  a.n.c.n.m.l.HuggingFaceModelLoader$ - Fetching hf://nixiesearch/e5-small-v2-onnx from HF: model=model_quantized.onnx tokenizer=tokenizer.json
14:05:08.946 INFO  a.n.core.nn.model.HuggingFaceClient - found model_quantized.onnx in cache
14:05:08.946 INFO  a.n.core.nn.model.HuggingFaceClient - found tokenizer.json in cache
14:05:08.946 INFO  a.n.core.nn.model.HuggingFaceClient - found config.json in cache
14:05:08.953 WARN  a.d.h.t.HuggingFaceTokenizer - maxLength is not explicitly specified, use modelMaxLength: 512
14:05:09.030 INFO  a.n.core.nn.model.OnnxSession$ - Loaded ONNX model (size=32 MB inputs=List(input_ids, attention_mask, token_type_ids) outputs=List(last_hidden_state) dim=384)
14:05:09.032 INFO  a.nixiesearch.index.sync.LocalIndex$ - index movies opened
14:05:09.067 INFO  a.n.main.subcommands.IndexMode$ - ███╗   ██╗██╗██╗  ██╗██╗███████╗███████╗███████╗ █████╗ ██████╗  ██████╗██╗  ██╗
14:05:09.067 INFO  a.n.main.subcommands.IndexMode$ - ████╗  ██║██║╚██╗██╔╝██║██╔════╝██╔════╝██╔════╝██╔══██╗██╔══██╗██╔════╝██║  ██║
14:05:09.067 INFO  a.n.main.subcommands.IndexMode$ - ██╔██╗ ██║██║ ╚███╔╝ ██║█████╗  ███████╗█████╗  ███████║██████╔╝██║     ███████║
14:05:09.067 INFO  a.n.main.subcommands.IndexMode$ - ██║╚██╗██║██║ ██╔██╗ ██║██╔══╝  ╚════██║██╔══╝  ██╔══██║██╔══██╗██║     ██╔══██║
14:05:09.067 INFO  a.n.main.subcommands.IndexMode$ - ██║ ╚████║██║██╔╝ ██╗██║███████╗███████║███████╗██║  ██║██║  ██║╚██████╗██║  ██║
14:05:09.067 INFO  a.n.main.subcommands.IndexMode$ - ╚═╝  ╚═══╝╚═╝╚═╝  ╚═╝╚═╝╚══════╝╚══════╝╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝╚═╝  ╚═╝
14:05:09.067 INFO  a.n.main.subcommands.IndexMode$ -                                                                                
14:05:09.069 INFO  a.nixiesearch.util.source.URLReader$ - reading file /home/shutty/tmp/nixie-experiments/movies.jsonl
May 24, 2024 2:05:11 PM org.apache.lucene.internal.vectorization.VectorizationProvider lookup
WARNING: Java vector incubator module is not readable. For optimal vector performance, pass '--add-modules jdk.incubator.vector' to enable Vector API.
14:05:12.338 INFO  ai.nixiesearch.index.Indexer - index commit, seqnum=4
14:05:12.340 INFO  ai.nixiesearch.index.Indexer - generated manifest for files List(_0.cfe, _0.cfs, _0.si, index.json, segments_1, write.lock)
14:05:15.272 INFO  ai.nixiesearch.index.Indexer - index commit, seqnum=8
14:05:15.273 INFO  ai.nixiesearch.index.Indexer - generated manifest for files List(_0.cfe, _0.cfs, _0.si, _1.cfe, _1.cfs, _1.si, index.json, segments_2, write.lock)
14:05:16.577 INFO  ai.nixiesearch.index.Indexer - index commit, seqnum=12
14:05:16.578 INFO  ai.nixiesearch.index.Indexer - generated manifest for files List(_0.cfe, _0.cfs, _0.si, _1.cfe, _1.cfs, _1.si, _2.cfe, _2.cfs, _2.si, index.json, segments_3, write.lock)
14:05:16.583 INFO  a.n.core.nn.model.BiEncoderCache - closing model hf://nixiesearch/e5-small-v2-onnx
14:05:16.585 INFO  a.n.main.subcommands.IndexMode$ - indexing done

```

After indexing, your index location will contain a set of Lucene index files:

```shell
$ ls -l indexes/movies/
total 8808
-rw-r--r-- 1 shutty shutty     549 May 24 14:05 _0.cfe
-rw-r--r-- 1 shutty shutty 3657700 May 24 14:05 _0.cfs
-rw-r--r-- 1 shutty shutty     321 May 24 14:05 _0.si
-rw-r--r-- 1 shutty shutty     549 May 24 14:05 _1.cfe
-rw-r--r-- 1 shutty shutty 3658540 May 24 14:05 _1.cfs
-rw-r--r-- 1 shutty shutty     321 May 24 14:05 _1.si
-rw-r--r-- 1 shutty shutty     549 May 24 14:05 _2.cfe
-rw-r--r-- 1 shutty shutty 1664108 May 24 14:05 _2.cfs
-rw-r--r-- 1 shutty shutty     321 May 24 14:05 _2.si
-rw-r--r-- 1 shutty shutty    2497 May 24 14:06 index.json
-rw-r--r-- 1 shutty shutty     318 May 24 14:05 segments_3
-rw-r--r-- 1 shutty shutty       0 May 24 14:05 write.lock
```

You can start Nixiesearch then in a `Searcher` mode with the `search` subcommand:

```shell
docker run -i -t nixiesearch/nixiesearch:latest -v <dir>:/data search --config /data/conf.yaml
```

## Online indexing

Online indexing with a REST API is mainly meant for experimentation and for small-scale document ingestion jobs.

REST API for indexing can be used in both [distributed](../../deployment/distributed/overview.md) (e.g. when you have separate deployments for Searcher and Indexer) and [standalone](../../deployment/standalone.md) (e.g. when Searcher and Indexer are colocated in a single node and single process) modes.

To run Nixiesearch in [a standalone mode](../../deployment/standalone.md), use the `standalone` CLI subcommand:

```shell
docker run -i -t nixiesearch/nixiesearch:latest -v <dir>:/data standalone --config /data/conf.yaml
```

You will see in the logs that Indexer HTTP service is listening on a port 8080:

```
14:11:50.616 INFO  ai.nixiesearch.index.Searcher$ - opening index movies
14:11:50.759 INFO  a.n.main.subcommands.StandaloneMode$ - ███╗   ██╗██╗██╗  ██╗██╗███████╗███████╗███████╗ █████╗ ██████╗  ██████╗██╗  ██╗
14:11:50.759 INFO  a.n.main.subcommands.StandaloneMode$ - ████╗  ██║██║╚██╗██╔╝██║██╔════╝██╔════╝██╔════╝██╔══██╗██╔══██╗██╔════╝██║  ██║
14:11:50.759 INFO  a.n.main.subcommands.StandaloneMode$ - ██╔██╗ ██║██║ ╚███╔╝ ██║█████╗  ███████╗█████╗  ███████║██████╔╝██║     ███████║
14:11:50.759 INFO  a.n.main.subcommands.StandaloneMode$ - ██║╚██╗██║██║ ██╔██╗ ██║██╔══╝  ╚════██║██╔══╝  ██╔══██║██╔══██╗██║     ██╔══██║
14:11:50.759 INFO  a.n.main.subcommands.StandaloneMode$ - ██║ ╚████║██║██╔╝ ██╗██║███████╗███████║███████╗██║  ██║██║  ██║╚██████╗██║  ██║
14:11:50.759 INFO  a.n.main.subcommands.StandaloneMode$ - ╚═╝  ╚═══╝╚═╝╚═╝  ╚═╝╚═╝╚══════╝╚══════╝╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝╚═╝  ╚═╝
14:11:50.759 INFO  a.n.main.subcommands.StandaloneMode$ -                                                                                
14:11:50.787 INFO  o.h.ember.server.EmberServerBuilder - Ember-Server service bound to address: [::]:8080
```

After that you can HTTP POST the documents file to the `_index` [indexing endpoint](../../api.md):

```shell
$ curl -XPOST -d @movies.jsonl http://localhost:8080/movies/_index

{"result":"created","took":8113}
```

You will also see the indexing progress in the logs:

```
14:12:11.026 INFO  ai.nixiesearch.api.IndexRoute - PUT /movies/_index
May 24, 2024 2:12:11 PM org.apache.lucene.internal.vectorization.VectorizationProvider lookup
WARNING: Java vector incubator module is not readable. For optimal vector performance, pass '--add-modules jdk.incubator.vector' to enable Vector API.
14:12:12.175 INFO  ai.nixiesearch.core.PrintProgress$ - processed 320 indexed docs, perf=279rps
14:12:13.366 INFO  ai.nixiesearch.core.PrintProgress$ - processed 704 indexed docs, perf=322rps
14:12:14.549 INFO  ai.nixiesearch.core.PrintProgress$ - processed 1088 indexed docs, perf=325rps
14:12:15.762 INFO  ai.nixiesearch.core.PrintProgress$ - processed 1472 indexed docs, perf=317rps
14:12:16.896 INFO  ai.nixiesearch.core.PrintProgress$ - processed 1792 indexed docs, perf=282rps
14:12:18.105 INFO  ai.nixiesearch.core.PrintProgress$ - processed 2176 indexed docs, perf=318rps
14:12:19.140 INFO  ai.nixiesearch.api.IndexRoute - completed indexing, took 8113ms
14:12:19.143 INFO  ai.nixiesearch.api.API$ - HTTP/1.1 POST /movies/_index
14:12:19.143 INFO  ai.nixiesearch.api.API$ - HTTP/1.1 200 OK
```

Finally, flush the index to disk, using the `_flush` endpoint:

```shell
$ curl -XPOST http://localhost:8080/movies/_flush
```

Nixiesearch will synchonously flush the index, and acknowledge the request with an empty (200 OK) response.

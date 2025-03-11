# Prometheus metrics

Nixiesearch exposes a [Prometheus](https://prometheus.io/docs/introduction/overview/)-compatible metrics exposed on the `/metrics` endpoint. The [same port as for REST API](../../reference/config.md#core-config) (8080 by default) is used for serving. Raw metrics values can be inspected with the following cURL request:

```shell
curl http://localhost:8080/metrics
```


```
# HELP jvm_threads_state Current count of threads by state
# TYPE jvm_threads_state gauge
jvm_threads_state{state="BLOCKED"} 0.0
jvm_threads_state{state="NEW"} 0.0
jvm_threads_state{state="RUNNABLE"} 6.0
jvm_threads_state{state="TERMINATED"} 0.0
jvm_threads_state{state="TIMED_WAITING"} 7.0
jvm_threads_state{state="UNKNOWN"} 0.0
jvm_threads_state{state="WAITING"} 14.0

# HELP nixiesearch_fs_data_available_bytes Available space on device
# TYPE nixiesearch_fs_data_available_bytes gauge
nixiesearch_fs_data_available_bytes{device="/dev/mapper/private"} 3.1363534848E10
nixiesearch_fs_data_available_bytes{device="/dev/nvme0n1p2"} 6.05224292352E11
nixiesearch_fs_data_available_bytes{device="/dev/root"} 4.886433792E9
```

## Collecting metrics

To scrape metrics using a vanilla Prometheus server, you can use a [`static_configs`](https://prometheus.io/docs/prometheus/latest/getting_started/) to define an endpoint:

```yaml
global:
  scrape_interval: 15s

scrape_configs:
- job_name: nixiesearch
  static_configs:
  - targets: ['nixiesearch_host:8080']
```

If nixiesearch cluster is running inside [Kubernetes]() as a [`Deployment`](TODO) without explicit node identity (e.g. pods are ephemeral, and they have no static IP and hostnames), a [k8s service discovery](https://prometheus.io/docs/prometheus/latest/configuration/configuration/#kubernetes_sd_config) should be used to dynamically scrape all Nixiesearch nodes.

```yaml
scrape_configs:
  - job_name: 'nixiesearch'
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        action: keep
        regex: nixiesearch
      - source_labels: [__meta_kubernetes_pod_ip]
        action: replace
        target_label: __address__
        regex: (.*)
        replacement: ${1}:8080
      - source_labels: [__meta_kubernetes_namespace]
        target_label: namespace
      - source_labels: [__meta_kubernetes_pod_name]
        target_label: pod
    metrics_path: /metrics
```

## Exported metrics

### Search metrics

{{ read_csv('search.csv') }}

### System metrics

{{ read_csv('system.csv') }}

### JVM metrics

As Nixiesearch is a [JVM](https://en.wikipedia.org/wiki/Java_virtual_machine) application, it exposes rich set of JVM-related metrics. We use a standard set of JVM instrumentation metrics from the [Prometheus Java Client](https://prometheus.github.io/client_java/instrumentation/jvm/).

#### JVM Garbage Collector Metrics

The data is coming from [GarbageCollectorMXBean](https://docs.oracle.com/en/java/javase/21/docs/api/java.management/java/lang/management/GarbageCollectorMXBean.html).

```
# HELP jvm_gc_collection_seconds Time spent in a given JVM garbage collector in seconds.
# TYPE jvm_gc_collection_seconds summary
jvm_gc_collection_seconds_count{gc="PS MarkSweep"} 0
jvm_gc_collection_seconds_sum{gc="PS MarkSweep"} 0.0
jvm_gc_collection_seconds_count{gc="PS Scavenge"} 0
jvm_gc_collection_seconds_sum{gc="PS Scavenge"} 0.0
```

#### JVM Memory Metrics

Source of the data - [MemoryPoolMXBean](https://docs.oracle.com/en/java/javase/21/docs/api/java.management/java/lang/management/MemoryPoolMXBean.html)

```
# HELP jvm_memory_committed_bytes Committed (bytes) of a given JVM memory area.
# TYPE jvm_memory_committed_bytes gauge
jvm_memory_committed_bytes{area="heap"} 4.98597888E8
jvm_memory_committed_bytes{area="nonheap"} 1.1993088E7
# HELP jvm_memory_init_bytes Initial bytes of a given JVM memory area.
# TYPE jvm_memory_init_bytes gauge
jvm_memory_init_bytes{area="heap"} 5.20093696E8
jvm_memory_init_bytes{area="nonheap"} 2555904.0
# HELP jvm_memory_max_bytes Max (bytes) of a given JVM memory area.
# TYPE jvm_memory_max_bytes gauge
jvm_memory_max_bytes{area="heap"} 7.38983936E9
jvm_memory_max_bytes{area="nonheap"} -1.0
# HELP jvm_memory_objects_pending_finalization The number of objects waiting in the finalizer queue.
# TYPE jvm_memory_objects_pending_finalization gauge
jvm_memory_objects_pending_finalization 0.0
# HELP jvm_memory_pool_collection_committed_bytes Committed after last collection bytes of a given JVM memory pool.
# TYPE jvm_memory_pool_collection_committed_bytes gauge
jvm_memory_pool_collection_committed_bytes{pool="PS Eden Space"} 1.30023424E8
jvm_memory_pool_collection_committed_bytes{pool="PS Old Gen"} 3.47078656E8
jvm_memory_pool_collection_committed_bytes{pool="PS Survivor Space"} 2.1495808E7
# HELP jvm_memory_pool_collection_init_bytes Initial after last collection bytes of a given JVM memory pool.
# TYPE jvm_memory_pool_collection_init_bytes gauge
jvm_memory_pool_collection_init_bytes{pool="PS Eden Space"} 1.30023424E8
jvm_memory_pool_collection_init_bytes{pool="PS Old Gen"} 3.47078656E8
jvm_memory_pool_collection_init_bytes{pool="PS Survivor Space"} 2.1495808E7
# HELP jvm_memory_pool_collection_max_bytes Max bytes after last collection of a given JVM memory pool.
# TYPE jvm_memory_pool_collection_max_bytes gauge
jvm_memory_pool_collection_max_bytes{pool="PS Eden Space"} 2.727870464E9
jvm_memory_pool_collection_max_bytes{pool="PS Old Gen"} 5.542248448E9
jvm_memory_pool_collection_max_bytes{pool="PS Survivor Space"} 2.1495808E7
# HELP jvm_memory_pool_collection_used_bytes Used bytes after last collection of a given JVM memory pool.
# TYPE jvm_memory_pool_collection_used_bytes gauge
jvm_memory_pool_collection_used_bytes{pool="PS Eden Space"} 0.0
jvm_memory_pool_collection_used_bytes{pool="PS Old Gen"} 1249696.0
jvm_memory_pool_collection_used_bytes{pool="PS Survivor Space"} 0.0
# HELP jvm_memory_pool_committed_bytes Committed bytes of a given JVM memory pool.
# TYPE jvm_memory_pool_committed_bytes gauge
jvm_memory_pool_committed_bytes{pool="Code Cache"} 4128768.0
jvm_memory_pool_committed_bytes{pool="Compressed Class Space"} 917504.0
jvm_memory_pool_committed_bytes{pool="Metaspace"} 6946816.0
jvm_memory_pool_committed_bytes{pool="PS Eden Space"} 1.30023424E8
jvm_memory_pool_committed_bytes{pool="PS Old Gen"} 3.47078656E8
jvm_memory_pool_committed_bytes{pool="PS Survivor Space"} 2.1495808E7
# HELP jvm_memory_pool_init_bytes Initial bytes of a given JVM memory pool.
# TYPE jvm_memory_pool_init_bytes gauge
jvm_memory_pool_init_bytes{pool="Code Cache"} 2555904.0
jvm_memory_pool_init_bytes{pool="Compressed Class Space"} 0.0
jvm_memory_pool_init_bytes{pool="Metaspace"} 0.0
jvm_memory_pool_init_bytes{pool="PS Eden Space"} 1.30023424E8
jvm_memory_pool_init_bytes{pool="PS Old Gen"} 3.47078656E8
jvm_memory_pool_init_bytes{pool="PS Survivor Space"} 2.1495808E7
# HELP jvm_memory_pool_max_bytes Max bytes of a given JVM memory pool.
# TYPE jvm_memory_pool_max_bytes gauge
jvm_memory_pool_max_bytes{pool="Code Cache"} 2.5165824E8
jvm_memory_pool_max_bytes{pool="Compressed Class Space"} 1.073741824E9
jvm_memory_pool_max_bytes{pool="Metaspace"} -1.0
jvm_memory_pool_max_bytes{pool="PS Eden Space"} 2.727870464E9
jvm_memory_pool_max_bytes{pool="PS Old Gen"} 5.542248448E9
jvm_memory_pool_max_bytes{pool="PS Survivor Space"} 2.1495808E7
# HELP jvm_memory_pool_used_bytes Used bytes of a given JVM memory pool.
# TYPE jvm_memory_pool_used_bytes gauge
jvm_memory_pool_used_bytes{pool="Code Cache"} 4065472.0
jvm_memory_pool_used_bytes{pool="Compressed Class Space"} 766680.0
jvm_memory_pool_used_bytes{pool="Metaspace"} 6659432.0
jvm_memory_pool_used_bytes{pool="PS Eden Space"} 7801536.0
jvm_memory_pool_used_bytes{pool="PS Old Gen"} 1249696.0
jvm_memory_pool_used_bytes{pool="PS Survivor Space"} 0.0
# HELP jvm_memory_used_bytes Used bytes of a given JVM memory area.
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap"} 9051232.0
jvm_memory_used_bytes{area="nonheap"} 1.1490688E7
```

#### JVM Buffer Pool Metrics

The data is coming from the [BufferPoolMXBean](https://docs.oracle.com/en/java/javase/21/docs/api/java.management/java/lang/management/BufferPoolMXBean.html).

```
# HELP jvm_buffer_pool_capacity_bytes Bytes capacity of a given JVM buffer pool.
# TYPE jvm_buffer_pool_capacity_bytes gauge
jvm_buffer_pool_capacity_bytes{pool="direct"} 8192.0
jvm_buffer_pool_capacity_bytes{pool="mapped"} 0.0
# HELP jvm_buffer_pool_used_buffers Used buffers of a given JVM buffer pool.
# TYPE jvm_buffer_pool_used_buffers gauge
jvm_buffer_pool_used_buffers{pool="direct"} 1.0
jvm_buffer_pool_used_buffers{pool="mapped"} 0.0
# HELP jvm_buffer_pool_used_bytes Used bytes of a given JVM buffer pool.
# TYPE jvm_buffer_pool_used_bytes gauge
jvm_buffer_pool_used_bytes{pool="direct"} 8192.0
jvm_buffer_pool_used_bytes{pool="mapped"} 0.0
```

#### JVM Class Loading Metrics

The data is coming from [ClassLoadingMXBean](https://docs.oracle.com/en/java/javase/21/docs/api/java.management/java/lang/management/ClassLoadingMXBean.html).

```
# HELP jvm_classes_currently_loaded The number of classes that are currently loaded in the JVM
# TYPE jvm_classes_currently_loaded gauge
jvm_classes_currently_loaded 1109.0
# HELP jvm_classes_loaded_total The total number of classes that have been loaded since the JVM has started execution
# TYPE jvm_classes_loaded_total counter
jvm_classes_loaded_total 1109.0
# HELP jvm_classes_unloaded_total The total number of classes that have been unloaded since the JVM has started execution
# TYPE jvm_classes_unloaded_total counter
jvm_classes_unloaded_total 0.0
```
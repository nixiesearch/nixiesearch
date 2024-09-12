# GPU support

Nixiesearch supports both CPU and GPU inference for embeddings and generative models:

* for embedding inference the [ONNXRuntime]() is used with CPU and CUDA Execution Providers.
* for GenAI inference the [llamacpp]() backend is used with both CUDA and CPU support built-in.

All official Nixiesearch Docker containers on [hub.docker.com/u/nixiesearch](https://hub.docker.com/u/nixiesearch) starting from a `0.3.0` version are published in two flavours:

* with a `-gpu` suffix: `nixiesearch/nixiesearch:0.3.0-amd64-gpu` which includes GPU support. These containers include GPU native libraries and CUDA runtime, so their size is huge: ~6Gb.
* without the suffix: `nixiesearch/nixiesearch:0.3.0`. No GPU native libs, no CUDA runtime, slim size of 700Mb.

!!! note

    Nixiesearch currently supports CUDA12 on Linux-x86_64 only. If you need AArch64 support, please [open a ticket](https://github.com/nixiesearch/nixiesearch/issues) with your use-case.


!!! note

    Nixiesearch currently supports only single-GPU inference for embedding models. If your host has 2+ GPUs, Nixiesearch will use the first one only. Generative models can use any number of GPUs.

## GPU pass-through with Docker

To perform a GPU pass-through from your host machine to the Nixiesearch docker container, you need to have [nvidia-container-toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html) installed and configured. [AWS NVIDIA GPU-Optimized AMI](https://aws.amazon.com/marketplace/pp/prodview-7ikjtg3um26wq) and [GCP Deep Learning VM Image](https://cloud.google.com/deep-learning-vm) support this out of the box.

To validate that the pass-through works correctly, pass the `--gpus all` flag to docker for a sample workload:

```shell
docker run --gpus all ubuntu nvidia-smi
```

To run Nixiesearch in a standalone mode with GPU support:

```shell
docker run --gpus all -itv <dir>:/data nixiesearch/nixiesearch:latest-gpu \
    standalone -c /data/config.yml
```

When GPU gets detected, you'll get the following log:

```
12:42:22.450 INFO  ai.nixiesearch.main.Main$ - ONNX CUDA EP Found: GPU Build
12:42:22.492 INFO  ai.nixiesearch.main.Main$ - GPU 0: NVIDIA GeForce RTX 4090
12:42:22.492 INFO  ai.nixiesearch.main.Main$ - GPU 1: NVIDIA GeForce RTX 4090
...
14:11:23.629 INFO  a.n.c.n.m.embedding.EmbedModelDict$ - loading model.onnx
14:11:23.629 INFO  a.n.c.n.m.embedding.EmbedModelDict$ - Fetching hf://nixiesearch/e5-small-v2-onnx from HF: model=model.onnx tokenizer=tokenizer.json
14:11:23.630 INFO  a.n.core.nn.model.HuggingFaceClient - found cached /home/shutty/cache/models/nixiesearch/e5-small-v2-onnx/model.onnx file for requested nixiesearch/e5-small-v2-onnx/model.onnx
14:11:23.630 INFO  a.n.core.nn.model.HuggingFaceClient - found cached /home/shutty/cache/models/nixiesearch/e5-small-v2-onnx/tokenizer.json file for requested nixiesearch/e5-small-v2-onnx/tokenizer.json
14:11:23.631 INFO  a.n.core.nn.model.HuggingFaceClient - found cached /home/shutty/cache/models/nixiesearch/e5-small-v2-onnx/config.json file for requested nixiesearch/e5-small-v2-onnx/config.json
14:11:23.636 INFO  a.n.c.n.m.e.EmbedModel$OnnxEmbedModel$ - Embedding model scheduled for GPU inference
...
ggml_cuda_init: found 2 CUDA devices:
  Device 0: NVIDIA GeForce RTX 4090, compute capability 8.9, VMM: yes
  Device 1: NVIDIA GeForce RTX 4090, compute capability 8.9, VMM: yes
llm_load_tensors: ggml ctx size =    0.38 MiB
llm_load_tensors: offloading 24 repeating layers to GPU
llm_load_tensors: offloading non-repeating layers to GPU
llm_load_tensors: offloaded 25/25 layers to GPU
llm_load_tensors:        CPU buffer size =   137.94 MiB
llm_load_tensors:      CUDA0 buffer size =   104.91 MiB
llm_load_tensors:      CUDA1 buffer size =   226.06 MiB
...........................................
llama_new_context_with_model: n_ctx      = 32768
llama_new_context_with_model: n_batch    = 2048
llama_new_context_with_model: n_ubatch   = 512
llama_new_context_with_model: flash_attn = 0
llama_new_context_with_model: freq_base  = 1000000.0
llama_new_context_with_model: freq_scale = 1
llama_kv_cache_init:      CUDA0 KV buffer size =   208.00 MiB
llama_kv_cache_init:      CUDA1 KV buffer size =   176.00 MiB
llama_new_context_with_model: KV self size  =  384.00 MiB, K (f16):  192.00 MiB, V (f16):  192.00 MiB
llama_new_context_with_model:  CUDA_Host  output buffer size =     2.90 MiB
llama_new_context_with_model: pipeline parallelism enabled (n_copies=4)
llama_new_context_with_model:      CUDA0 compute buffer size =  1166.01 MiB
llama_new_context_with_model:      CUDA1 compute buffer size =  1166.02 MiB
llama_new_context_with_model:  CUDA_Host compute buffer size =   257.77 MiB
llama_new_context_with_model: graph nodes  = 846
llama_new_context_with_model: graph splits = 3
[INFO] initializing slots n_slots=4
[INFO] new slot id_slot=0 n_ctx_slot=8192
[INFO] new slot id_slot=1 n_ctx_slot=8192
[INFO] new slot id_slot=2 n_ctx_slot=8192
[INFO] new slot id_slot=3 n_ctx_slot=8192
[INFO] model loaded

```
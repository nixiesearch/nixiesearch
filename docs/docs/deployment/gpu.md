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

    Nixiesearch currently supports only single-GPU inference. If your host has 2+ GPUs, Nixiesearch will use the first one only.

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
```
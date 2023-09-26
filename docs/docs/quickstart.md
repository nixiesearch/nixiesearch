# Quickstart

This guide shows how to install Nixiesearch on a single machine using Docker. We will run the service in a [standalone](reference/cli/standalone.md) mode, [index](concepts/index.md) a corpus of documents and run a couple of [search](concepts/search.md) queries.

## Prerequisites

This guide assumes that you already have the following available:

* Docker: [Docker Desktop](https://docs.docker.com/engine/install/) for Mac/Windows, or Docker for Linux.
* Operating system: Linux, macOS, Windows with [WSL2](https://learn.microsoft.com/en-us/windows/wsl/install).
* Architecture: x86_64. On Mac M1+, you should be able to run x86_64 docker images on arm64 Macs with [Rosetta](https://levelup.gitconnected.com/docker-on-apple-silicon-mac-how-to-run-x86-containers-with-rosetta-2-4a679913a0d5).
* Memory: 2Gb dedicated to Docker.

## Getting the dataset

For this quickstart we will use a sample of the [MSMARCO](https://microsoft.github.io/msmarco/) dataset, which contains text documents from the [Bing](https://www.bing.com/) search engine. 
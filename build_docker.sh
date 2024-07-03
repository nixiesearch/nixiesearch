#!/bin/bash

set -euxo pipefail

docker run --rm --privileged multiarch/qemu-user-static --reset -p yes

PLATFORM=amd64 sbt -mem 5000 dockerBuildAndPush
PLATFORM=arm64 sbt -mem 5000 dockerBuildAndPush

docker manifest create nixiesearch/nixiesearch:$V nixiesearch/nixiesearch:$V-arm64 nixiesearch/nixiesearch:$V-amd64
docker manifest rm nixiesearch/nixiesearch:latest
docker manifest create nixiesearch/nixiesearch:latest nixiesearch/nixiesearch:$V-arm64 nixiesearch/nixiesearch:$V-amd64
docker manifest push nixiesearch/nixiesearch:$V
docker manifest push nixiesearch/nixiesearch:latest
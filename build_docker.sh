#!/bin/bash

set -euxo pipefail
V=$1
docker run --rm --privileged multiarch/qemu-user-static --reset -p yes

# Build JVM images
PLATFORM=amd64 GPU=false sbt -mem 5000 dockerBuildAndPush
PLATFORM=amd64 GPU=true sbt -mem 5000 dockerBuildAndPush
docker push nixiesearch/nixiesearch:latest-gpu
PLATFORM=arm64 sbt -mem 5000 dockerBuildAndPush

# Create JVM manifests
docker manifest create nixiesearch/nixiesearch:$V nixiesearch/nixiesearch:$V-arm64 nixiesearch/nixiesearch:$V-amd64
docker manifest rm nixiesearch/nixiesearch:latest
docker manifest create nixiesearch/nixiesearch:latest nixiesearch/nixiesearch:$V-arm64 nixiesearch/nixiesearch:$V-amd64
docker manifest push nixiesearch/nixiesearch:$V
docker manifest push nixiesearch/nixiesearch:latest

# Build native image (amd64 only)
PLATFORM=amd64 sbt -mem 5000 dockerNative
docker push nixiesearch/nixiesearch:$V-native-amd64
docker push nixiesearch/nixiesearch:latest-native
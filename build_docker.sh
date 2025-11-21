#!/bin/bash

set -euxo pipefail
VERSION=$1

docker run --rm --privileged multiarch/qemu-user-static --reset -p yes

# Build JVM images
PLATFORM=amd64 GPU=false sbt -mem 5000 dockerBuildAndPush
docker tag nixiesearch/nixiesearch:$VERSION-amd64 $ECR_REGISTRY:$VERSION-amd64
docker push $ECR_REGISTRY:$VERSION-amd64

PLATFORM=amd64 GPU=true sbt -mem 5000 dockerBuildAndPush
docker push nixiesearch/nixiesearch:latest-gpu
docker tag nixiesearch/nixiesearch:latest-gpu $ECR_REGISTRY:latest-gpu
docker push $ECR_REGISTRY:latest-gpu

PLATFORM=arm64 sbt -mem 5000 dockerBuildAndPush
docker tag nixiesearch/nixiesearch:$VERSION-arm64 $ECR_REGISTRY:$VERSION-arm64
docker push $ECR_REGISTRY:$VERSION-arm64

# Create JVM manifests
docker manifest create nixiesearch/nixiesearch:$VERSION nixiesearch/nixiesearch:$VERSION-arm64 nixiesearch/nixiesearch:$VERSION-amd64
docker manifest rm nixiesearch/nixiesearch:latest
docker manifest create nixiesearch/nixiesearch:latest nixiesearch/nixiesearch:$VERSION-arm64 nixiesearch/nixiesearch:$VERSION-amd64
docker manifest push nixiesearch/nixiesearch:$VERSION
docker manifest push nixiesearch/nixiesearch:latest

# Create ECR JVM manifests
ECR_REGISTRY=public.ecr.aws/nixiesearch/nixiesearch
aws ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin public.ecr.aws/nixiesearch

docker manifest create $ECR_REGISTRY:$VERSION $ECR_REGISTRY:$VERSION-arm64 $ECR_REGISTRY:$VERSION-amd64
docker manifest create $ECR_REGISTRY:latest $ECR_REGISTRY:$VERSION-arm64 $ECR_REGISTRY:$VERSION-amd64
docker manifest push $ECR_REGISTRY:$VERSION
docker manifest push $ECR_REGISTRY:latest

# Build native image (amd64 only)
PLATFORM=amd64 sbt -mem 5000 dockerNative
docker push nixiesearch/nixiesearch:$VERSION-native-amd64
docker push nixiesearch/nixiesearch:latest-native

# Push native images to ECR
docker tag nixiesearch/nixiesearch:$VERSION-native-amd64 $ECR_REGISTRY:$VERSION-native-amd64
docker push $ECR_REGISTRY:$VERSION-native-amd64
docker tag nixiesearch/nixiesearch:latest-native $ECR_REGISTRY:latest-native
docker push $ECR_REGISTRY:latest-native
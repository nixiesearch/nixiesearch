#!/bin/bash

set -euxo pipefail
V=$1
ECR_REGISTRY=public.ecr.aws/f3z9z3z0/nixiesearch

aws ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin public.ecr.aws/f3z9z3z0

docker run --rm --privileged multiarch/qemu-user-static --reset -p yes

# Build JVM images
PLATFORM=amd64 GPU=false sbt -mem 5000 dockerBuildAndPush
docker tag nixiesearch/nixiesearch:$V-amd64 $ECR_REGISTRY:$V-amd64
docker push $ECR_REGISTRY:$V-amd64

PLATFORM=amd64 GPU=true sbt -mem 5000 dockerBuildAndPush
docker push nixiesearch/nixiesearch:latest-gpu
docker tag nixiesearch/nixiesearch:latest-gpu $ECR_REGISTRY:latest-gpu
docker push $ECR_REGISTRY:latest-gpu

PLATFORM=arm64 sbt -mem 5000 dockerBuildAndPush
docker tag nixiesearch/nixiesearch:$V-arm64 $ECR_REGISTRY:$V-arm64
docker push $ECR_REGISTRY:$V-arm64

# Create JVM manifests
docker manifest create nixiesearch/nixiesearch:$V nixiesearch/nixiesearch:$V-arm64 nixiesearch/nixiesearch:$V-amd64
docker manifest rm nixiesearch/nixiesearch:latest
docker manifest create nixiesearch/nixiesearch:latest nixiesearch/nixiesearch:$V-arm64 nixiesearch/nixiesearch:$V-amd64
docker manifest push nixiesearch/nixiesearch:$V
docker manifest push nixiesearch/nixiesearch:latest

# Create ECR JVM manifests
docker manifest create $ECR_REGISTRY:$V $ECR_REGISTRY:$V-arm64 $ECR_REGISTRY:$V-amd64
docker manifest create $ECR_REGISTRY:latest $ECR_REGISTRY:$V-arm64 $ECR_REGISTRY:$V-amd64
docker manifest push $ECR_REGISTRY:$V
docker manifest push $ECR_REGISTRY:latest

# Build native image (amd64 only)
PLATFORM=amd64 sbt -mem 5000 dockerNative
docker push nixiesearch/nixiesearch:$V-native-amd64
docker push nixiesearch/nixiesearch:latest-native

# Push native images to ECR
docker tag nixiesearch/nixiesearch:$V-native-amd64 $ECR_REGISTRY:$V-native-amd64
docker push $ECR_REGISTRY:$V-native-amd64
docker tag nixiesearch/nixiesearch:latest-native $ECR_REGISTRY:latest-native
docker push $ECR_REGISTRY:latest-native
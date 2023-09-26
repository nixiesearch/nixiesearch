#!/bin/sh

set -euxo pipefail

VERSION=$1
WORKDIR=`mktemp -d -t nixie-quickstart-XXXXX`

wget https://huggingface.co/datasets/nixiesearch/msmarco-10k/resolve/main/corpus.json.gz -O $WORKDIR/corpus.json.gz
gzip -d $WORKDIR/corpus.json.gz

CONTAINER_ID=`docker run -d -p 8080:8080 nixiesearch/nixiesearch:latest standalone`

timeout 180 sh -c 'until curl -v http://localhost:8080/health; do sleep 1; done'

curl -XPOST -d @$WORKDIR/corpus.json http://localhost:8080/msmarco/_index
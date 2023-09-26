#!/bin/sh

set -euxo pipefail

WORKDIR=`mktemp -d -t nixie-quickstart-`

wget https://huggingface.co/datasets/nixiesearch/msmarco-10k/resolve/main/corpus.json.gz
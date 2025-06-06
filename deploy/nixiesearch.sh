#!/bin/bash

# This is a docker entrypoint script.

set -euxo pipefail
OPTS=${JAVA_OPTS:-"-Xmx2g -verbose:gc --add-modules jdk.incubator.vector"}

exec /usr/bin/java $OPTS --enable-preview -jar /app/nixiesearch.jar "$@"


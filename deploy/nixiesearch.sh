#!/bin/bash

set -euxo pipefail
OPTS=${JAVA_OPTS:-"-Xmx1g -verbose:gc --add-modules jdk.incubator.vector"}

exec /usr/bin/java $OPTS --enable-preview -jar /app/nixiesearch.jar "$@"


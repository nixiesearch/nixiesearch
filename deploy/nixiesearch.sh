#!/bin/bash

set -euxo pipefail
OPTS=${JAVA_OPTS:-"-Xmx1g -verbose:gc"}

exec /usr/bin/java $OPTS --enable-preview -jar /app/nixiesearch.jar "$@"


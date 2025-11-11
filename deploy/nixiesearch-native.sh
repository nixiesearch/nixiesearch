#!/bin/sh

# This is a docker entrypoint script.

OPTS=${JAVA_OPTS:-"-Xmx2g -verbose:gc"}

exec /nixiesearch "$@"


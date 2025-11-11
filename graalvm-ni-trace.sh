#!/bin/sh

echo "JAVA_HOME=$JAVA_HOME"
echo "Regenerating GraalVM native-image reachability metadata..."

$JAVA_HOME/bin/java -agentlib:native-image-agent=config-output-dir=native-image-configs -jar target/scala-3.7.3/nixiesearch.jar trace

echo "Done"
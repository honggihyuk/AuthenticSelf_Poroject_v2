#!/bin/bash
set -e
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:/mnt/c/Gradle/gradle-8.14.3/bin:$PATH"
cd /mnt/c/AuthenticSelf_Project/AuthenticSelf_v3/src/backend
echo "=== java ==="
java -version 2>&1 | head -2
echo "=== gradle ==="
gradle -v 2>&1 | head -2
echo "=== wrapper ==="
gradle wrapper --gradle-version 8.14.3 2>&1 | tail -5
echo "=== gradlew ==="
ls gradlew* 2>/dev/null | head -3

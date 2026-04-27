#!/bin/bash
set -e
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///var/run/docker.sock
export TESTCONTAINERS_RYUK_DISABLED=true
cd /mnt/c/AuthenticSelf_Project/AuthenticSelf_v3/src/backend
echo "=== env verify ==="
env | grep -E "DOCKER|TESTCONTAINER" | head -5
echo "=== one-test run with --info ==="
./gradlew test --tests 'com.authenticself.migration.V1InitSchemaMigrationTest' --info --no-daemon --console=plain 2>&1 | grep -E "Checking the system|Could not find|Accessing docker|DockerClient|docker|WARN|ERROR|Traceback" | head -40
echo "=== gradle exit=${PIPESTATUS[0]} ==="

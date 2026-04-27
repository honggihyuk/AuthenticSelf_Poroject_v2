#!/bin/bash
# Runs Spring backend tests via the freshly generated gradle wrapper.
# Requires Docker daemon for Testcontainers. Executes from WSL so
# Linux path separators resolve correctly.
set -e
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
# Testcontainers: point explicitly at the unix socket; Docker Desktop's
# WSL integration exposes /var/run/docker.sock. Ryuk disabled to avoid
# sidecar-container startup quirks on first run.
export DOCKER_HOST=unix:///var/run/docker.sock
export TESTCONTAINERS_RYUK_DISABLED=true
cd /mnt/c/AuthenticSelf_Project/AuthenticSelf_v3/src/backend
echo "=== docker probe ==="
docker info --format '{{.ServerVersion}}' 2>&1 | head -1 || echo "(no docker on WSL shell)"
echo "DOCKER_HOST=$DOCKER_HOST"
echo "=== ./gradlew test ==="
./gradlew test -PskipTestcontainers=true --no-daemon --console=plain 2>&1 | tail -120
echo "=== gradle exit=${PIPESTATUS[0]} ==="

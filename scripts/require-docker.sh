#!/usr/bin/env sh
# Fails the build with a clear message when Docker is unavailable, so the Docker-backed gates under
# `mvn clean package -Pfull` - the documentation lint, the gitleaks secret scan, and the Qodana
# inspection scan - never fail with an obscure error instead. Docker is a toolchain dependency of the
# full verification, exactly as the JDK and Maven are.
set -eu

gates="the documentation lint, the gitleaks secret scan and the Qodana inspection scan"

if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: Docker is required for $gates, but 'docker' is not on PATH." >&2
    echo "       Install Docker (https://docs.docker.com/get-docker/) and retry 'mvn clean package -Pfull'." >&2
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    echo "ERROR: Docker is installed but its daemon is not reachable; $gates need it." >&2
    echo "       Start Docker and retry 'mvn clean package -Pfull'." >&2
    exit 1
fi

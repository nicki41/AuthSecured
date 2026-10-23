#!/bin/bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
GRADLE_CACHE_DIR="${GRADLE_USER_HOME:-$PROJECT_DIR/.gradle_home}"
PROJECT_VERSION="$(sed -n 's/.*version = "\([^"]*\)".*/\1/p' "$PROJECT_DIR/build.gradle.kts" | head -n 1)"

if [ -z "$PROJECT_VERSION" ]; then
    echo "Could not determine project version from build.gradle.kts" >&2
    exit 2
fi

GRADLE_USER_HOME="$GRADLE_CACHE_DIR" "$PROJECT_DIR/gradlew" \
    clean test :platform-paper:build :platform-fabric:build

mkdir -p "$PROJECT_DIR/build/libs"
cp "$PROJECT_DIR/platform-paper/build/libs/authsecured-paper-$PROJECT_VERSION.jar" "$PROJECT_DIR/build/libs/"
cp "$PROJECT_DIR/platform-fabric/build/libs/authsecured-fabric-$PROJECT_VERSION+mc26.3.jar" "$PROJECT_DIR/build/libs/"

if [ "${AUTHSECURED_FULL_MATRIX:-false}" = "true" ]; then
    GRADLE_USER_HOME="$GRADLE_CACHE_DIR" "$PROJECT_DIR/build_fabric_matrix.sh"
fi

echo "Build and tests completed successfully:"
ls -lh "$PROJECT_DIR/build/libs"/authsecured-*.jar

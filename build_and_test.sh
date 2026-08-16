#!/bin/bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
GRADLE_CACHE_DIR="${GRADLE_USER_HOME:-$PROJECT_DIR/.gradle_home}"

GRADLE_USER_HOME="$GRADLE_CACHE_DIR" "$PROJECT_DIR/gradlew" \
    clean test :platform-paper:shadowJar :platform-fabric:build

mkdir -p "$PROJECT_DIR/build/libs"
cp "$PROJECT_DIR/platform-paper/build/libs/authsecured-paper-1.1.0.jar" "$PROJECT_DIR/build/libs/"
cp "$PROJECT_DIR/platform-fabric/build/libs/authsecured-fabric-1.1.0+mc26.2.jar" "$PROJECT_DIR/build/libs/"

if [ "${AUTHSECURED_FULL_MATRIX:-false}" = "true" ]; then
    GRADLE_USER_HOME="$GRADLE_CACHE_DIR" "$PROJECT_DIR/build_fabric_matrix.sh"
fi

echo "Build and tests completed successfully:"
ls -lh "$PROJECT_DIR/build/libs"/authsecured-*.jar

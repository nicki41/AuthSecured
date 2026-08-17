#!/bin/bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
GRADLE_CACHE_DIR="${GRADLE_USER_HOME:-$PROJECT_DIR/.gradle_home}"

GRADLE_USER_HOME="$GRADLE_CACHE_DIR" "$PROJECT_DIR/gradlew" \
    :core:dependencies \
    :database:dependencies \
    :platform-common:dependencies \
    :platform-paper:dependencies \
    :platform-fabric:dependencies

echo "Dependencies resolved into the Gradle cache."

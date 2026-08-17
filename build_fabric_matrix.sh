#!/bin/bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
GRADLE_CACHE_DIR="${GRADLE_USER_HOME:-$PROJECT_DIR/.gradle_home}"
OUTPUT_DIR="$PROJECT_DIR/build/libs"

versions=(
    "1.21" "1.21.1" "1.21.2" "1.21.3" "1.21.4" "1.21.5"
    "1.21.6" "1.21.7" "1.21.8" "1.21.9" "1.21.10" "1.21.11"
    "26.1" "26.1.1" "26.1.2" "26.2"
)

mkdir -p "$OUTPUT_DIR"
matrix_from="${FABRIC_MATRIX_FROM:-}"
matrix_started=false
[ -z "$matrix_from" ] && matrix_started=true

for minecraft_version in "${versions[@]}"; do
    if [ "$matrix_started" = false ]; then
        if [ "$minecraft_version" = "$matrix_from" ]; then
            matrix_started=true
        else
            continue
        fi
    fi
    echo "Building AuthSecured Fabric for Minecraft $minecraft_version"
    build_succeeded=false
    for attempt in 1 2 3; do
        if GRADLE_USER_HOME="$GRADLE_CACHE_DIR" "$PROJECT_DIR/gradlew" \
            :platform-fabric:clean :platform-fabric:build \
            -Pminecraft_version="$minecraft_version"; then
            build_succeeded=true
            break
        fi
        echo "Build attempt $attempt failed for $minecraft_version; retrying..." >&2
        sleep 5
    done
    if [ "$build_succeeded" = false ]; then
        echo "Fabric build failed after 3 attempts: $minecraft_version" >&2
        exit 1
    fi
    cp "$PROJECT_DIR/platform-fabric/build/libs/authsecured-fabric-1.1.0+mc$minecraft_version.jar" \
        "$OUTPUT_DIR/"
done

if [ "$matrix_started" = false ]; then
    echo "Unknown FABRIC_MATRIX_FROM version: $matrix_from" >&2
    exit 2
fi

echo "Fabric matrix build completed:"
ls -lh "$OUTPUT_DIR"/authsecured-fabric-*.jar

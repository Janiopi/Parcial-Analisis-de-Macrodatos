#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn package
mkdir -p build dist
cp target/cc531.jar build/cc531.jar
cp target/cc531*.jar dist/
echo "Built target/cc531*.jar and copied them to dist/"

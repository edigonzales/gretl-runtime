#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME muss auf ein JDK 25 zeigen." >&2
  exit 2
fi

"$ROOT_DIR/mvnw" -q -pl gretl-dist -am package -DskipTests
"$ROOT_DIR/gretl-dist/target/dist/bin/gretl" \
  operations --format adoc --lang de \
  --output "$ROOT_DIR/docs/reference/generated"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME muss auf ein JDK 25 zeigen." >&2
  exit 2
fi

"$ROOT_DIR/gradlew" -q installDist
"$ROOT_DIR/gretl-cli/build/install/gretl-runtime/bin/gretl" \
  operations --format adoc --lang de \
  --output "$ROOT_DIR/docs/reference/generated"

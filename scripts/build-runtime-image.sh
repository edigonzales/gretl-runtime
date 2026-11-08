#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
exec "${GRETL_PYTHON:-python3}" "$ROOT/scripts/build-runtime-image.py" "$@"

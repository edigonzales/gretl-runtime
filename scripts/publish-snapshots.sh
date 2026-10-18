#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

if [[ -z "${MAVEN_USERNAME:-}" || -z "${MAVEN_PASSWORD:-}" ]]; then
  echo "MAVEN_USERNAME und MAVEN_PASSWORD muessen gesetzt sein." >&2
  echo "Es wurden keine Zugangsdaten an das Repository gesendet." >&2
  exit 2
fi

exec "$ROOT_DIR/mvnw" \
  -s "$ROOT_DIR/docs/implementation/publish-settings.xml" \
  -Ppublish-snapshots \
  clean deploy "$@"

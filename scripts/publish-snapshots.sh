#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

if [[ -z "${MAVEN_USERNAME:-}" || -z "${MAVEN_PASSWORD:-}" ]]; then
  echo "MAVEN_USERNAME und MAVEN_PASSWORD muessen gesetzt sein." >&2
  echo "Es wurden keine Zugangsdaten an das Repository gesendet." >&2
  exit 2
fi

# Qualification never receives deployment-only arguments or test-skip flags.
EVIDENCE_DIR=${GRETL_PUBLICATION_EVIDENCE:-"$ROOT_DIR/.gretl-verification/publication-$(python3 -c 'import uuid; print(uuid.uuid4())')"}
"$ROOT_DIR/scripts/verify-job-matrix.sh" --mode SCRIPT --scope local --output "$EVIDENCE_DIR/standard"
"$ROOT_DIR/scripts/verify-job-matrix.sh" --mode ALL --scope full --output "$EVIDENCE_DIR/full"

exec "$ROOT_DIR/mvnw" -B \
  -s "$ROOT_DIR/docs/implementation/publish-settings.xml" \
  -Ppublish-snapshots \
  -DskipTests -DskipITs deploy "$@"

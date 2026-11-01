#!/usr/bin/env bash
# Small sequential wrapper around the existing Maven lifecycle and evidence audit.
set -euo pipefail
umask 077
ROOT=$(cd "$(dirname "$0")/.." && pwd)
MODE=SCRIPT
SCOPE=local
DEST=
while [ "$#" -gt 0 ]; do
  case "$1" in
    --mode) MODE=${2:?missing mode}; shift 2 ;;
    --scope) SCOPE=${2:?missing scope}; shift 2 ;;
    --output) DEST=${2:?missing output}; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done
case "$MODE" in SCRIPT|JAR) ;; *) echo 'This phase supports --mode SCRIPT|JAR' >&2; exit 2 ;; esac
case "$SCOPE" in local|full) ;; *) echo 'Expected --scope local|full' >&2; exit 2 ;; esac
cd "$ROOT"
SHA=$(git rev-parse HEAD)
if [ -n "$(git status --porcelain --untracked-files=normal)" ]; then
  echo 'Candidate verification requires a clean checkout' >&2; exit 2
fi
RUN_ID=$(python3 -c 'import uuid; print(uuid.uuid4())')
if [ -z "$DEST" ]; then DEST="$ROOT/.gretl-verification/$RUN_ID"; fi
case "$DEST" in /*) ;; *) DEST="$ROOT/$DEST" ;; esac
DEST=$(python3 -c 'from pathlib import Path; import sys; print(Path(sys.argv[1]).resolve())' "$DEST")
case "$DEST/" in "$ROOT/"*/target/*|"$ROOT/target/"*) echo 'Evidence must survive Maven clean' >&2; exit 2 ;; esac
mkdir -p "$DEST"
OUT="$DEST/$MODE"
mkdir "$OUT"
printf '%s\n' "$RUN_ID" > "$OUT/run-id.txt"
printf '%s\n' "$SHA" > "$OUT/source-sha.txt"
uname -sm > "$OUT/platform.txt"
args=(-B clean verify "-Dgretl.test.executionMode=$MODE" "-Dgretl.test.runId=$RUN_ID" "-Dgretl.test.candidateSha=$SHA")
if [ "$SCOPE" = full ]; then args=(-B -Pgeotools-probe,backing-services clean verify "-Dgretl.test.executionMode=$MODE" "-Dgretl.test.runId=$RUN_ID" "-Dgretl.test.candidateSha=$SHA"); docker version > "$OUT/docker-version.txt"; fi
printf '%s\n' './mvnw' "${args[@]}" > "$OUT/maven-argv.txt"
set +e
./mvnw "${args[@]}" 2>&1 | tee "$OUT/maven.log"
PIPE_EXITS=("${PIPESTATUS[@]}")
BUILD_EXIT=${PIPE_EXITS[0]}
if [ "$BUILD_EXIT" -eq 0 ] && [ "${PIPE_EXITS[1]}" -ne 0 ]; then BUILD_EXIT=${PIPE_EXITS[1]}; fi
set -e
printf '%s\n' "$BUILD_EXIT" > "$OUT/maven-exit.txt"
AUDIT_EXIT=125
if [ "$BUILD_EXIT" -eq 0 ]; then
  set +e
  python3 scripts/audit-job-evidence.py --source "$SHA" --mode "$MODE" --scope "$SCOPE" --run-id "$RUN_ID" > "$OUT/audit.log" 2>&1
  AUDIT_EXIT=$?
  set -e
fi
printf '%s\n' "$AUDIT_EXIT" > "$OUT/audit-exit.txt"
python3 scripts/preserve-job-evidence.py "$ROOT" "$OUT/evidence" "$SCOPE"
if [ "$BUILD_EXIT" -ne 0 ]; then exit "$BUILD_EXIT"; fi
if [ "$AUDIT_EXIT" -ne 0 ]; then cat "$OUT/audit.log" >&2; exit "$AUDIT_EXIT"; fi
[ "$SHA" = "$(git rev-parse HEAD)" ] && [ -z "$(git status --porcelain --untracked-files=normal)" ]
python3 - "$OUT" "$MODE" "$SCOPE" "$SHA" "$RUN_ID" <<'PY'
import json,sys
from pathlib import Path
out,mode,scope,sha,run=sys.argv[1:]
Path(out,'complete.json').write_text(json.dumps(dict(schemaVersion=1,mode=mode,scope=scope,sourceSha=sha,suiteRunId=run,mavenExit=0,auditExit=0),indent=2)+'\n')
PY

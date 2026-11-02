#!/usr/bin/env bash
# Sequential existing Maven gates, audited evidence, and cleanup of only this run's Docker resources.
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
case "$MODE" in SCRIPT|JAR|IMAGE|ALL) ;; *) echo 'Expected --mode SCRIPT|JAR|IMAGE|ALL' >&2; exit 2 ;; esac
case "$SCOPE" in local|full) ;; *) echo 'Expected --scope local|full' >&2; exit 2 ;; esac
cd "$ROOT"
SHA=$(git rev-parse HEAD)
if [ -n "$(git status --porcelain --untracked-files=normal)" ]; then echo 'Candidate verification requires a clean checkout' >&2; exit 2; fi
RUN_ID=$(python3 -c 'import uuid; print(uuid.uuid4())')
if [ -z "$DEST" ]; then DEST="$ROOT/.gretl-verification/$RUN_ID"; fi
case "$DEST" in /*) ;; *) DEST="$ROOT/$DEST" ;; esac
DEST=$(python3 -c 'from pathlib import Path; import sys; print(Path(sys.argv[1]).resolve())' "$DEST")
case "$DEST/" in "$ROOT/"*/target/*|"$ROOT/target/"*) echo 'Evidence must survive Maven clean' >&2; exit 2 ;; esac
mkdir -p "$DEST"
if [ "$MODE" = ALL ]; then
  for selected in SCRIPT JAR IMAGE; do "$ROOT/scripts/verify-job-matrix.sh" --mode "$selected" --scope "$SCOPE" --output "$DEST"; done
  # Recheck retained bytes after every later clean before granting a matrix PASS.
  for selected in SCRIPT JAR IMAGE; do
    SELECTED_RUN_ID=$(cat "$DEST/$selected/run-id.txt")
    python3 "$ROOT/scripts/audit-job-evidence.py" --source "$SHA" --mode "$selected" --scope "$SCOPE" --run-id "$SELECTED_RUN_ID" --evidence-root "$DEST/$selected/evidence" > "$DEST/$selected/final-audit.log" 2>&1
  done
  python3 - "$DEST" "$SHA" "$SCOPE" <<'PY'
import json,sys
from pathlib import Path
root=Path(sys.argv[1]);sha,scope=sys.argv[2:];summaries=[]
for mode in ('SCRIPT','JAR','IMAGE'):
 complete=json.loads((root/mode/'complete.json').read_text())
 assert complete['sourceSha']==sha and complete['scope']==scope and complete['mode']==mode and complete['mavenExit']==complete['auditExit']==0
 summary=json.loads((root/mode/'evidence/gretl-integration-tests/target/qualification/summary.json').read_text());summaries.append(summary)
 assert summary['mode']==mode and summary['sourceRevision']==sha and summary['sourceDirty'] is False
 if scope=='full':assert len(summary['requiredPositiveOperations'])==44
common=[]
for mode in ('SCRIPT','JAR','IMAGE'):
 rows=json.loads((root/mode/'evidence/gretl-integration-tests/target/qualification/executions.json').read_text())
 common.append({r['scenarioId']:r['input']['treeSha256'] for r in rows if r['testFamily']=='COMMON'})
assert common[0] and common[0]==common[1]==common[2], 'COMMON inputs differ across modes'
(root/'complete.json').write_text(json.dumps(dict(schemaVersion=1,sourceSha=sha,scope=scope,modes=['SCRIPT','JAR','IMAGE'],identicalCommonInputs=len(common[0]),summaries=summaries),indent=2)+'\n')
PY
  exit 0
fi
OUT="$DEST/$MODE"
mkdir "$OUT"
printf '%s\n' "$RUN_ID" > "$OUT/run-id.txt"
printf '%s\n' "$SHA" > "$OUT/source-sha.txt"
uname -sm > "$OUT/platform.txt"
NETWORK=
MAVEN_WRAPPER_PID=
cleanup_owned_resources() {
  if [ -n "$MAVEN_WRAPPER_PID" ] && kill -0 "$MAVEN_WRAPPER_PID" 2>/dev/null; then
    kill -TERM "$MAVEN_WRAPPER_PID" 2>/dev/null || true
    wait "$MAVEN_WRAPPER_PID" || true
  fi
  if [ -n "$NETWORK" ]; then
    local ids
    ids=$(docker container ls --all --quiet --filter "label=gretl.verification.run=$RUN_ID") || return 1
    printf '%s\n' "$ids" > "$OUT/cleanup-container-ids.txt"
    if [ -n "$ids" ]; then docker container rm --force $ids >> "$OUT/cleanup.log" 2>&1 || return 1; fi
    if docker network ls --quiet --filter "name=^$NETWORK$" | python3 -c 'import sys; sys.exit(not bool(sys.stdin.read().strip()))'; then
      docker network rm "$NETWORK" >> "$OUT/cleanup.log" 2>&1 || return 1
    fi
    NETWORK=
  fi
}
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'status=$?; cleanup_owned_resources || status=1; exit "$status"' EXIT
args=(-B clean verify "-Dgretl.test.executionMode=$MODE" "-Dgretl.test.runId=$RUN_ID" "-Dgretl.test.candidateSha=$SHA")
PROFILES=
if [ "$SCOPE" = full ]; then PROFILES=geotools-probe,backing-services; docker version > "$OUT/docker-version.txt"; fi
if [ "$MODE" = IMAGE ]; then
  docker version > "$OUT/docker-version.txt"
  uid=$(id -u); gid=$(id -g)
  if [ "$uid" = 0 ]; then echo 'IMAGE bind tests require an actual non-root host user' >&2; exit 2; fi
  NETWORK="gretl-verification-$RUN_ID"
  docker network create --label "gretl.verification.run=$RUN_ID" "$NETWORK" > "$OUT/network-id.txt"
  export GRETL_VERIFICATION_RUN_ID="$RUN_ID"
  if [ -n "$PROFILES" ]; then PROFILES="$PROFILES,job-image"; else PROFILES=job-image; fi
  args+=("-Dgretl.test.imageNetwork=$NETWORK" "-Dgretl.test.imageUser=$uid:$gid")
  if [ "$(uname -s)" = Darwin ]; then args+=(-Dgretl.test.httpImageHost=host.docker.internal -Dgretl.test.imageExtraHost=); else args+=(-Dgretl.test.httpImageHost=services -Dgretl.test.imageExtraHost=services:host-gateway); fi
fi
if [ -n "$PROFILES" ]; then args=(-P"$PROFILES" "${args[@]}"); fi
printf '%s\n' './mvnw' "${args[@]}" > "$OUT/maven-argv.txt"
set +e
python3 scripts/run-maven-gate.py "$OUT" ./mvnw "${args[@]}" &
MAVEN_WRAPPER_PID=$!
wait "$MAVEN_WRAPPER_PID"
BUILD_EXIT=$?
MAVEN_WRAPPER_PID=
set -e
AUDIT_EXIT=125
if [ "$BUILD_EXIT" -eq 0 ]; then
  set +e
  python3 scripts/audit-job-evidence.py --source "$SHA" --mode "$MODE" --scope "$SCOPE" --run-id "$RUN_ID" > "$OUT/audit.log" 2>&1
  AUDIT_EXIT=$?
  set -e
fi
printf '%s\n' "$AUDIT_EXIT" > "$OUT/audit-exit.txt"
python3 scripts/preserve-job-evidence.py "$ROOT" "$OUT/evidence" "$SCOPE"
cleanup_owned_resources
if [ "$BUILD_EXIT" -ne 0 ]; then exit "$BUILD_EXIT"; fi
if [ "$AUDIT_EXIT" -ne 0 ]; then cat "$OUT/audit.log" >&2; exit "$AUDIT_EXIT"; fi
[ "$SHA" = "$(git rev-parse HEAD)" ] && [ -z "$(git status --porcelain --untracked-files=normal)" ]
python3 - "$OUT" "$MODE" "$SCOPE" "$SHA" "$RUN_ID" <<'PY'
import json,sys
from pathlib import Path
out,mode,scope,sha,run=sys.argv[1:]
Path(out,'complete.json').write_text(json.dumps(dict(schemaVersion=1,mode=mode,scope=scope,sourceSha=sha,suiteRunId=run,mavenExit=0,auditExit=0),indent=2)+'\n')
PY

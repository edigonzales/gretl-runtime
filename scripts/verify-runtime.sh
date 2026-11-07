#!/usr/bin/env bash
# One reactor, with an owned Docker network for the explicit IMAGE probes.
set -euo pipefail

cd "$(dirname "$0")/.."
if [ "$#" -ne 0 ]; then
  echo 'Usage: scripts/verify-runtime.sh' >&2
  exit 2
fi

docker info --format 'Docker server {{.ServerVersion}}'
uid=$(id -u)
gid=$(id -g)
if [ "$uid" = 0 ]; then
  echo 'IMAGE bind tests require a non-root host user' >&2
  exit 2
fi
run_id=$(python3 -c 'import uuid; print(uuid.uuid4())')
export GRETL_VERIFICATION_RUN_ID="$run_id"
label="gretl.verification.run=$run_id"
network=$(docker network create --label "$label" "gretl-verify-$run_id")
maven_pid=
image_workspace=
cleanup() {
  local status=$? ids cleanup_failed=0
  trap - EXIT INT TERM
  if [ -n "$maven_pid" ] && kill -0 "$maven_pid" 2>/dev/null; then
    kill -TERM "$maven_pid" 2>/dev/null || :
    wait "$maven_pid" || :
  fi
  if ids=$(docker container ls --all --quiet --filter "label=$label"); then
    if [ -n "$ids" ]; then
      while IFS= read -r id; do
        docker container rm --force "$id" || cleanup_failed=1
      done <<< "$ids"
    fi
  else
    cleanup_failed=1
  fi
  docker network rm "$network" || cleanup_failed=1
  if [ -n "$image_workspace" ]; then
    rm -r -- "$image_workspace" || cleanup_failed=1
  fi
  if [ "$status" -eq 0 ]; then status=$cleanup_failed; fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Keep Docker bind sources outside directories recreated by Maven clean.
image_workspace=$(mktemp -d /tmp/gretl-image-jobs.XXXXXX)

if [ "$(uname -s)" = Darwin ]; then
  http_host=host.docker.internal
  extra_host=
else
  http_host=services
  extra_host=services:host-gateway
fi

./mvnw -B -Pgeotools-probe,backing-services,job-image clean verify \
  "-Dgretl.test.runId=$run_id" "-Dgretl.test.imageNetwork=$network" \
  "-Dgretl.test.imageWorkspace=$image_workspace" "-Dgretl.test.imageUser=$uid:$gid" "-Dgretl.test.httpImageHost=$http_host" \
  "-Dgretl.test.imageExtraHost=$extra_host" &
maven_pid=$!
wait "$maven_pid"

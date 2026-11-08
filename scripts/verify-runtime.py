#!/usr/bin/env python3.12
"""Run the qualification reactor and clean up its own processes and Docker resources."""

from __future__ import annotations

import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import uuid


DOCKER_TIMEOUT = 15.0
TERM_TIMEOUT = 10.0
KILL_TIMEOUT = 5.0
CLEANUP_TIMEOUT = 60.0
POLL_INTERVAL = 0.1


def docker(*args: str, deadline: float | None = None, missing: str | None = None) -> str:
    timeout = DOCKER_TIMEOUT if deadline is None else min(DOCKER_TIMEOUT, deadline - time.monotonic())
    description = "docker " + " ".join(args)
    if timeout <= 0:
        raise RuntimeError(f"Cleanup deadline elapsed before {description}")
    try:
        result = subprocess.run(["docker", *args], capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired as error:
        raise RuntimeError(f"{description} timed out after {timeout:.1f}s") from error
    if result.returncode:
        detail = result.stderr.strip()
        if missing is None or missing not in detail.lower():
            raise RuntimeError(f"{description} exited {result.returncode}: {detail}")
    return result.stdout.strip()


def stop_maven_group(process: subprocess.Popen, deadline: float) -> None:
    # start_new_session makes the child's PID its session ID and process group ID.
    group = process.pid
    if group <= 0 or group == os.getpgrp():
        raise RuntimeError(f"Refusing to signal unowned process group {group}")

    def signal_group(sig: int) -> bool:
        process.poll()  # Reap the group leader even when children remain in the group.
        try:
            os.killpg(group, sig)
        except ProcessLookupError:
            return False
        return True

    for sig, grace in ((signal.SIGTERM, TERM_TIMEOUT), (signal.SIGKILL, KILL_TIMEOUT)):
        if not signal_group(sig):
            return
        until = min(deadline, time.monotonic() + grace)
        while time.monotonic() < until:
            if not signal_group(0):
                return
            time.sleep(min(POLL_INTERVAL, max(0, until - time.monotonic())))
    if signal_group(0):
        raise RuntimeError(f"Maven process group {group} survived TERM and KILL")


def cleanup(process: subprocess.Popen | None, label: str | None, workspace: Path | None) -> bool:
    deadline = time.monotonic() + CLEANUP_TIMEOUT
    errors = []

    def attempt(operation, *args, **kwargs):
        try:
            return operation(*args, **kwargs)
        except (OSError, RuntimeError) as error:
            errors.append(str(error))
            return None

    if process is not None:
        attempt(stop_maven_group, process, deadline)
    if label is not None:
        containers = attempt(docker, "container", "ls", "--all", "--quiet", "--filter",
                             f"label={label}", deadline=deadline)
        for container in (containers or "").splitlines():
            attempt(docker, "container", "rm", "--force", container, deadline=deadline,
                    missing=f"no such container: {container.lower()}")
        networks = attempt(docker, "network", "ls", "--quiet", "--filter",
                           f"label={label}", deadline=deadline)
        for network in (networks or "").splitlines():
            attempt(docker, "network", "rm", network, deadline=deadline, missing=f"network {network.lower()} not found")
    if workspace is not None:
        attempt(shutil.rmtree, workspace)
    for error in errors:
        print(f"Verification cleanup: {error}", file=sys.stderr)
    return not errors


def verify(repo: Path) -> int:
    received_signal = 0

    def record_signal(signum, _frame):
        nonlocal received_signal
        if not received_signal:
            received_signal = signum

    previous_handlers = {sig: signal.signal(sig, record_signal) for sig in (signal.SIGINT, signal.SIGTERM)}
    process = label = workspace = None
    result = 1
    try:
        uid, gid = os.getuid(), os.getgid()
        if uid == 0:
            print("IMAGE bind tests require a non-root host user", file=sys.stderr)
            return 2
        print(docker("info", "--format", "Docker server {{.ServerVersion}}"), flush=True)
        if not received_signal:
            run_id = str(uuid.uuid4())
            label = f"gretl.verification.run={run_id}"
            network = docker("network", "create", "--label", label, f"gretl-verify-{run_id}")
            if not network:
                raise RuntimeError("Docker returned no ID for the verification network")
        if not received_signal:
            # Maven clean can recreate target directories while Docker still holds bind sources.
            workspace = Path(tempfile.mkdtemp(prefix="gretl-image-jobs.", dir="/tmp"))
            http_host, extra_host = (
                ("host.docker.internal", "") if sys.platform == "darwin"
                else ("services", "services:host-gateway")
            )
            environment = os.environ | {
                "GRETL_VERIFICATION_RUN_ID": run_id,
                "GRETL_PYTHON": sys.executable,
            }
            process = subprocess.Popen([
                "./mvnw", "-B", "-Pgeotools-probe,backing-services,job-image", "clean", "verify",
                f"-Dgretl.test.runId={run_id}", f"-Dgretl.test.imageNetwork={network}",
                f"-Dgretl.test.imageWorkspace={workspace}", f"-Dgretl.test.imageUser={uid}:{gid}",
                f"-Dgretl.test.httpImageHost={http_host}", f"-Dgretl.test.imageExtraHost={extra_host}",
            ], cwd=repo, env=environment, start_new_session=True)
            while not received_signal:
                try:
                    exit_code = process.wait(timeout=POLL_INTERVAL)
                    result = exit_code if exit_code >= 0 else 128 - exit_code
                    break
                except subprocess.TimeoutExpired:
                    continue
    except (OSError, RuntimeError) as error:
        print(f"Verification: {error}", file=sys.stderr)
    finally:
        try:
            clean = cleanup(process, label, workspace)
        finally:
            for sig, handler in previous_handlers.items():
                signal.signal(sig, handler)
    if received_signal:
        return 128 + received_signal
    return result if clean or result else 1


def main() -> int:
    if sys.version_info[:2] != (3, 12):
        print("Runtime verification requires Python 3.12", file=sys.stderr)
        return 2
    if len(sys.argv) != 1:
        print("Usage: python3.12 scripts/verify-runtime.py", file=sys.stderr)
        return 2
    return verify(Path(__file__).resolve().parents[1])


if __name__ == "__main__":
    sys.exit(main())

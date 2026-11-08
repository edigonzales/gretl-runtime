#!/usr/bin/env python3.12
"""Export a bounded, allowlisted failure summary from qualification test output."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import stat
import sys
import tempfile
from xml.etree import ElementTree


MAX_FILES = 100  # Includes the index.
MAX_BYTES = 10 * 1024 * 1024
MAX_LOG = 64 * 1024
MAX_INDEX = 256 * 1024
MAX_REPORT = 2 * 1024 * 1024
MAX_REPORTS = 400
MAX_REPORT_BYTES = 24 * 1024 * 1024
MAX_CASES = 100
MAX_ENTRIES = 1000
RUN_ID = re.compile(r"[A-Za-z0-9_-]{1,64}")
LOG_NAME = re.compile(r"[1-4]-(SCRIPT|JAR|IMAGE)-(stdout|stderr)\.log")
TEST_NAME = re.compile(r"[A-Za-z0-9_.$#(), -]{1,300}(?:\[\d{1,5}\])?")
SCENARIO = re.compile(r"[A-Za-z0-9._-]+-[0-9a-f-]{36}")
SENSITIVE_LINE = re.compile(
    r"password|passwd|authorization|cookie|token|credential|secret|api[_-]?key|"
    r"\b(?:environment|system[-_ ]?properties)\s*[:=]|"
    r'["\'](?:params|taskConfig|runtime|environment)["\']\s*:',
    re.IGNORECASE,
)


def safe_lines(text: str) -> str:
    lines = []
    for line in text.splitlines():
        if SENSITIVE_LINE.search(line):
            lines.append("[Sensitive line omitted]")
        elif len(line) > 4096:
            lines.append("[Oversized line omitted]")
        else:
            lines.append("".join(char for char in line if char.isprintable() or char == "\t"))
    return "\n".join(lines)


def safe_name(value: object) -> str:
    if isinstance(value, str) and TEST_NAME.fullmatch(value):
        return value
    return "[identity omitted]"


def directory(path: Path) -> bool:
    try:
        return stat.S_ISDIR(path.lstat().st_mode)
    except OSError:
        return False


def entries(path: Path, index: list[str]):
    if not directory(path):
        if path.is_symlink():
            index.append("Omitted a symbolic-link directory.")
        return
    try:
        with os.scandir(path) as items:
            for number, entry in enumerate(items):
                if number >= MAX_ENTRIES:
                    index.append("Directory entry scan limit reached; remaining entries omitted.")
                    break
                if entry.is_symlink():
                    index.append("Omitted a symbolic-link entry.")
                else:
                    yield entry
    except OSError:
        index.append("Omitted an unreadable diagnostics directory.")


def read_file(path: Path, limit: int, *, tail: bool = False, since: float = 0) -> tuple[bytes, bool]:
    # O_NONBLOCK also prevents a mislabelled FIFO from hanging collection.
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(descriptor, "rb") as stream:
        attributes = os.fstat(stream.fileno())
        if not stat.S_ISREG(attributes.st_mode) or attributes.st_mtime < since:
            raise ValueError("not a current regular file")
        clipped = attributes.st_size > limit
        if clipped and not tail:
            raise ValueError("size limit exceeded")
        if clipped:
            stream.seek(attributes.st_size - limit)
        data = stream.read(limit + 1)
        if len(data) > limit:
            if not tail:
                raise ValueError("size limit exceeded")
            data = data[:limit]
            clipped = True
        if clipped:
            _, _, data = data.partition(b"\n")
        return data, clipped


def xml(data: bytes):
    text = data.decode("utf-8")
    if "<!DOCTYPE" in text:
        raise ValueError("DOCTYPE is not allowed")
    return ElementTree.fromstring(text)


def maven_modules(repo: Path, index: list[str]):
    yield repo
    try:
        data, _ = read_file(repo / "pom.xml", MAX_REPORT)
        root = xml(data)
    except (OSError, ValueError, ElementTree.ParseError):
        index.append("Maven module list could not be read.")
        return
    modules = dict.fromkeys(item.text or "" for item in root.iterfind(".//{*}modules/{*}module"))
    for name in list(modules)[:MAX_REPORTS]:
        if not re.fullmatch(r"[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*", name):
            continue
        path = repo
        for part in name.split("/"):
            path /= part
            if not directory(path):
                if path.is_symlink():
                    index.append("Omitted a symbolic-link Maven module.")
                break
        else:
            yield path


def reports(repo: Path, index: list[str], since: float) -> None:
    count = bytes_read = failures = 0
    for module in maven_modules(repo, index):
        target = module / "target"
        if not directory(target):
            continue
        for report_dir in (target / "surefire-reports", target / "failsafe-reports"):
            for entry in entries(report_dir, index):
                if not entry.name.startswith("TEST-") or not entry.name.endswith(".xml"):
                    continue
                if count >= MAX_REPORTS or bytes_read >= MAX_REPORT_BYTES or failures >= MAX_CASES:
                    index.append("Maven report scan limit reached; remaining reports omitted.")
                    return
                count += 1
                try:
                    data, _ = read_file(Path(entry.path), min(MAX_REPORT, MAX_REPORT_BYTES - bytes_read),
                                        since=since)
                    bytes_read += len(data)
                    suite = xml(data)
                except (OSError, ValueError, ElementTree.ParseError):
                    index.append(f"Omitted unreadable, stale, oversized or malformed report {safe_name(entry.name)}.")
                    continue
                for case in suite.iter("testcase"):
                    for item in case:
                        if item.tag not in {"failure", "error"}:
                            continue
                        if failures >= MAX_CASES:
                            index.append("Maven failure limit reached; remaining cases omitted.")
                            return
                        failures += 1
                        test = safe_name(case.get("classname")) + "#" + safe_name(case.get("name"))
                        index.append(f"{item.tag}: {test} ({safe_name(item.get('type'))})")
                        message = item.get("message") or ""
                        if message:
                            index.append(safe_lines(message[:2048]) or "[Message omitted]")
                        if item.text:
                            index.append(safe_lines(item.text[:2048]) or "[Detail omitted]")
                        if len(message) > 2048 or len(item.text or "") > 2048:
                            index.append("  Maven failure detail truncated.")
    if not count:
        index.append("No Maven XML test reports were available.")
    elif not failures:
        index.append("No failed test cases were found in the readable Maven reports.")


class Index(list):
    def append(self, line: str) -> None:
        if len(self) < MAX_ENTRIES:
            super().append(line)
        elif len(self) == MAX_ENTRIES:
            super().append("Index entry limit reached; remaining entries omitted.")


class Export:
    def __init__(self, output: Path):
        self.output = output
        self.index = Index(["GRETL qualification failure diagnostics", ""])
        self.files = 0
        self.bytes = 0
        self.limited = False

    def log(self, source: Path, label: str, trimmed: bool = False) -> None:
        if self.files >= MAX_FILES - 1 or self.bytes >= MAX_BYTES - MAX_INDEX:
            if not self.limited:
                self.index.append("Log export limit reached; remaining logs omitted.")
                self.limited = True
            return
        try:
            data, clipped = read_file(source, MAX_LOG, tail=True)
        except (OSError, ValueError):
            self.index.append(f"{label}: missing, unsafe or unreadable log omitted.")
            return
        safe = safe_lines(data.decode("utf-8", errors="replace")).encode("utf-8")
        budget = min(MAX_LOG, MAX_BYTES - MAX_INDEX - self.bytes)
        content = safe[:budget]
        shortened = trimmed or clipped or len(content) < len(safe)
        name = f"process-{self.files + 1:03}.log"
        (self.output / name).write_bytes(content)
        self.files += 1
        self.bytes += len(content)
        note = " (truncated)" if shortened else ""
        self.index.append(f"{name}: {label}{note}")

    def staged(self, repo: Path, run_id: str | None) -> None:
        if run_id is None:
            return
        root = repo / "gretl-integration-tests"
        for part in ("target", "failed-process-diagnostics", run_id):
            if not directory(root):
                return
            root /= part
        for count, entry in enumerate(entries(root, self.index)):
            if count >= MAX_CASES:
                self.index.append("Staged case scan limit reached; remaining cases omitted.")
                break
            if not entry.name.startswith("case-") or not entry.is_dir(follow_symlinks=False):
                continue
            case_dir = Path(entry.path)
            try:
                data, _ = read_file(case_dir / "case.json", 8192)
                case = json.loads(data)
                test = safe_name(case["test"])
                logs = case["logs"]
                if not isinstance(logs, list) or len(logs) > 8:
                    raise ValueError("invalid staged log list")
                for log in logs:
                    name = log["name"]
                    match = LOG_NAME.fullmatch(name)
                    if match:
                        self.log(case_dir / name, f"{test} · {match[1]} {match[2]}",
                                 trimmed=log.get("trimmed") is True)
                    else:
                        self.index.append(f"{test}: omitted a staged file outside the log allowlist.")
                if case.get("omitted"):
                    self.index.append(f"{test}: some process logs were unavailable or omitted during staging.")
            except (OSError, KeyError, TypeError, ValueError, RecursionError):
                self.index.append("Omitted malformed or unsafe staged process metadata.")

    def interrupted_image(self, workspace: Path | None) -> None:
        if workspace is None:
            return
        self.index.append("Interrupted IMAGE scenarios · test attribution incomplete.")
        for count, entry in enumerate(entries(workspace, self.index)):
            if count >= MAX_CASES:
                self.index.append("Interrupted IMAGE scenario limit reached; remaining scenarios omitted.")
                break
            if SCENARIO.fullmatch(entry.name) and entry.is_dir(follow_symlinks=False):
                for stream in ("stdout", "stderr"):
                    self.log(Path(entry.path) / f"job.{stream}.log", f"Interrupted IMAGE scenario {count + 1} · {stream}")

    def write_index(self) -> None:
        content = ("\n".join(self.index) + "\n").encode("utf-8")
        if len(content) > MAX_INDEX:
            note = b"\nIndex truncated; remaining entries omitted.\n"
            content = content[:MAX_INDEX - len(note)].rsplit(b"\n", 1)[0] + note
        (self.output / "index.txt").write_bytes(content)


def collect(repo: Path, output: Path, *, run_id: str | None = None,
            workspace: Path | None = None, since: float = 0) -> None:
    if run_id is not None and not RUN_ID.fullmatch(run_id):
        raise ValueError("Invalid verification run identifier")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".diagnostics-", dir=output.parent) as scratch:
        stage = Path(scratch) / "export"
        stage.mkdir(mode=0o700)
        export = Export(stage)
        reports(repo, export.index, since)
        export.staged(repo, run_id)
        export.interrupted_image(workspace)
        export.write_index()
        stage.rename(output)


def main() -> int:
    if sys.version_info[:2] != (3, 12):
        print("Failure diagnostics require Python 3.12", file=sys.stderr)
        return 2
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        collect(Path(__file__).resolve().parents[1], args.output)
    except (OSError, ValueError) as failure:
        print(f"Could not export failure diagnostics: {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

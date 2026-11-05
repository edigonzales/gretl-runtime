#!/usr/bin/env python3
"""Verify actual double-GZIP output bytes, not just a successful process exit."""
import gzip
import hashlib
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: python3 verify.py WORK_DIRECTORY")
outputs = list(Path(sys.argv[1]).rglob("input.txt.gz.gz"))
if len(outputs) != 1:
    raise SystemExit(f"Expected one double-GZIP output, found {len(outputs)}")
expected = Path(__file__).with_name("data").joinpath("input.txt").read_bytes()
actual = gzip.decompress(gzip.decompress(outputs[0].read_bytes()))
if actual != expected:
    raise SystemExit("Decompressed output differs from original bytes")
print(f"PASS: {len(actual)} original bytes; sha256={hashlib.sha256(actual).hexdigest()}")

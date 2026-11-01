#!/usr/bin/env python3
"""Expose emulator instrumentation failures in the GitHub check annotations."""
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
if not path.is_file():
    print('::error::Device test output was not captured.')
    sys.exit(0)
lines = path.read_text(errors='replace').splitlines()
start = next((i for i, line in enumerate(lines) if re.match(r'\s*(?:There (?:was|were)|FAILURES!!!|\d+\) )', line)), None)
selected = lines[start:start + 25] if start is not None else lines[-12:]
if not selected:
    selected = ['Device test runner produced no output.']
for line in selected:
    if line.strip():
        safe = line[:1000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
        print(f'::error::{safe}')

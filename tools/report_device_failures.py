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
start = next((i for i, line in enumerate(lines) if re.match(r"\s*(?:There (?:was|were)|FAILURES!!!|\d+\) )", line)), None)
if start is None:
    detail = "\n".join(lines[-80:]) or "Device test runner produced no output."
else:
    # Keep the complete nested causes and all failing tests in one annotation.
    detail = "\n".join(lines[start:start + 250])
safe = detail[:45000].replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
print(f"::error title=Android emulator tests failed::{safe}")

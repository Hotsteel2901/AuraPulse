#!/usr/bin/env python3
"""Surface concise Gradle and compiler failures as Actions annotations."""
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
if not path.is_file():
    print('::error::Android build failed before its log was created.')
    sys.exit(0)
lines = path.read_text(errors='replace').splitlines()
errors = []
for index, line in enumerate(lines):
    if re.match(r'\s*(?:e: |.*\.kt:\d+:\d+: error:)', line):
        errors.append(line.strip())
    if line.strip() == '* What went wrong:':
        # Gradle configuration errors often have no compiler-style prefix.
        for detail in lines[index + 1:index + 31]:
            if detail.startswith('* Try:') or detail.startswith('* Exception is:'):
                break
            if detail.strip():
                errors.append(detail.strip())
if not errors:
    errors = ['Android build failed; inspect the assemble step log for details.']
for line in dict.fromkeys(errors):
    safe = line[:1000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::error::{safe}')

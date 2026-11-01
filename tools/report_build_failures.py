#!/usr/bin/env python3
"""Surface concise compiler errors as Actions annotations when log downloads are unavailable."""
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
if not path.is_file():
    print('::error::Android build failed before its log was created.')
    sys.exit(0)
lines = path.read_text(errors='replace').splitlines()
errors = [line.strip() for line in lines if re.match(r'\s*(?:e: |.*\.kt:\d+:\d+: error:)', line)]
if not errors:
    errors = [line.strip() for line in lines if 'What went wrong:' in line or line.strip().startswith('> ')]
if not errors:
    errors = ['Android build failed; inspect the assemble step log for details.']
for line in errors[-20:]:
    safe = line[:1000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::error::{safe}')

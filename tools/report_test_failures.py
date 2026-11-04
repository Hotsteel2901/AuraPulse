#!/usr/bin/env python3
"""Surface JUnit failures as Actions annotations, including suite initialization errors."""
from pathlib import Path
import xml.etree.ElementTree as ET


def annotate(title: str, detail: str) -> None:
    # Escape workflow command delimiters and cap the annotation to keep it readable.
    escape = lambda value: value.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f"::error title=Android unit test failure::{escape((title + chr(10) + detail)[:18000])}")


failures = []
for report in sorted(Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
    root = ET.parse(report).getroot()
    for case in root.iter('testcase'):
        for failure in list(case.findall('failure')) + list(case.findall('error')):
            title = f"{case.get('classname', report.stem)}#{case.get('name', 'unknown')}"
            failures.append((title, failure.get('message', '') + '\n' + (failure.text or '')))
    for error in root.findall('error'):
        failures.append((root.get('name', report.stem), error.get('message', '') + '\n' + (error.text or '')))

for title, detail in failures[:8]:
    annotate(title, detail)
if len(failures) > 8:
    annotate('Additional failures', f'{len(failures) - 8} more failures are in the uploaded test report.')
if not failures:
    # Compilation and emulator startup failures may not produce JUnit XML at all.
    for name in ('/tmp/aurapulse-unit-tests.log', '/tmp/firebase-emulators.log'):
        log = Path(name)
        if log.exists():
            annotate(log.name, '\n'.join(log.read_text(encoding='utf-8', errors='replace').splitlines()[-100:]))
            break

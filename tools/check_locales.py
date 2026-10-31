#!/usr/bin/env python3
"""Fail if a supported locale drops a string or changes its format arguments."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1] / "app/src/main/res"
locales = ["values", "values-zh-rCN", "values-zh-rTW", "values-zh-rHK", "values-ja", "values-ko", "values-es", "values-de"]
def load(folder):
    tree = ET.parse(root / folder / "strings.xml")
    entries = [(item.attrib["name"], item.text or "") for item in tree.findall("string")]
    if len(dict(entries)) != len(entries):
        raise ValueError(f"Duplicate key in {folder}")
    return dict(entries)

def arguments(value):
    return sorted(re.findall(r"%\d+\$[sd]", value))

baseline = load(locales[0])
errors = []
for locale in locales[1:]:
    values = load(locale)
    missing = baseline.keys() - values.keys()
    extra = values.keys() - baseline.keys()
    if missing or extra:
        errors.append(f"{locale}: missing={sorted(missing)}, extra={sorted(extra)}")
    for key in baseline.keys() & values.keys():
        if not values[key].strip() or arguments(values[key]) != arguments(baseline[key]):
            errors.append(f"{locale}/{key}: empty or mismatched format arguments")
if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print(f"Locale check passed: {len(baseline)} strings in {len(locales)} resource sets.")

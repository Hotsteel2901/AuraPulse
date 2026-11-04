#!/usr/bin/env python3
"""Fail if a supported locale drops a string or changes its format arguments."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1] / "app/src/main/res"
baseline_folder = "values"
translated_folders = sorted(
    folder.name
    for folder in root.iterdir()
    if folder.is_dir() and folder.name.startswith("values-") and (folder / "strings.xml").is_file()
)
def load(folder):
    tree = ET.parse(root / folder / "strings.xml")
    entries = [(item.attrib["name"], item.text or "") for item in tree.findall("string")]
    if len(dict(entries)) != len(entries):
        raise ValueError(f"Duplicate key in {folder}")
    return dict(entries)

def arguments(value):
    return sorted(re.findall(r"%\d+\$[sd]", value))

baseline = load(baseline_folder)
errors = []
for locale in translated_folders:
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
folders = ", ".join([baseline_folder] + translated_folders)
print(f"Locale check passed: {len(baseline)} strings in {len(translated_folders) + 1} resource sets ({folders}).")

"""Fails screens whose text (read from a screenshot) shows a raw text key such as
"reminder_title_years". Only keys with an underscore are checked: a one-word key such as
"share" can't be told apart from the English word.

Usage: raw_keys_text.py lang/en.xml problems.txt shot1.txt shot2.txt ...
Each .txt holds the lines read from one screenshot (ocr.swift). Also fails when no text
could be read from any screen, so a broken reader can't pass silently.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

en_path, problems_path, *texts = sys.argv[1:]
en = ET.parse(en_path).getroot()
keys = [e.get("name") for e in en.iter() if e.tag in ("string", "plurals") and "_" in (e.get("name") or "")]
patterns = [(k, re.compile(rf"(?<![A-Za-z0-9_]){re.escape(k)}(?![A-Za-z0-9_])", re.I)) for k in keys]

hits, read = [], 0
for t in texts:
    body = Path(t).read_text(encoding="utf-8")
    if body.strip():
        read += 1
    for k, p in patterns:
        if p.search(body):
            hits.append(f"{Path(t).with_suffix('.png')}: shows the raw key \"{k}\"")

with open(problems_path, "a", encoding="utf-8") as f:
    if hits:
        f.write("\n".join(hits) + "\n")
    if read == 0:
        f.write("No text could be read from any screenshot, so the raw key check did not run.\n")
print(f"Read text on {read} of {len(texts)} screens, {len(hits)} raw key(s).")
print("\n".join(hits))

"""Fails a screen that shows a raw text key, such as "reminder_title_years" or "select".

Usage: raw_keys.py lang/en.xml screen.xml label problems.txt
screen.xml is an `adb shell uiautomator dump`. Every text and description on the screen
is checked against the key names in en.xml. A hit is added to problems.txt.
"""
import re
import sys
import xml.etree.ElementTree as ET

en_path, screen_path, label, problems_path = sys.argv[1:5]
en = ET.parse(en_path).getroot()
words = {e.get("name"): (e.text or "") for e in en.iter("string")}
keys = set(words) | {e.get("name") for e in en.iter("plurals")}
# A key whose English word is the key itself (none today) can't be told apart, so skip it.
keys = {k for k in keys if words.get(k) != k}
long_keys = [k for k in keys if "_" in k]
slot_re = re.compile(r"\{[a-z][a-z0-9_]*\}")

hits = []
for node in ET.parse(screen_path).getroot().iter("node"):
    for attr in ("text", "content-desc"):
        value = (node.get(attr) or "").strip()
        if not value:
            continue
        if (
            value in keys
            or any(re.search(rf"(?<![\w]){re.escape(k)}(?![\w])", value) for k in long_keys)
            or slot_re.search(value)
        ):
            hits.append(f"{label}: {attr} \"{value}\"")

if hits:
    with open(problems_path, "a", encoding="utf-8") as f:
        f.write("\n".join(hits) + "\n")
    print("\n".join(hits))

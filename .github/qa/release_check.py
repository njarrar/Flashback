"""Quick release checks that need no build. Run from the top of the repo:

    python3 .github/qa/release_check.py

Fails when:
  - the Android and iPhone versions differ, or the README has no "What's new" for them
  - a store folder lacks a language, a text file, or holds text longer than the store takes
  - the Android app asks for a permission that is not on the list below
  - code asks for a word that lang/en.xml does not have
  - a row of places/cities.tsv on main moved or changed
See docs/QA.md (gate 1).
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
problems = []
notes = []


def fail(text):
    problems.append(text)


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


# 1. Versions ---------------------------------------------------------------------------
gradle = read("android/app/build.gradle.kts")
pbx = read("ios/BackThen.xcodeproj/project.pbxproj")
android_name = re.search(r'versionName\s*=\s*"([\d.]+)"', gradle).group(1)
android_code = int(re.search(r"versionCode\s*=\s*(\d+)", gradle).group(1))
ios_names = set(re.findall(r"MARKETING_VERSION = ([\d.]+);", pbx))
ios_builds = set(re.findall(r"CURRENT_PROJECT_VERSION = (\d+);", pbx))
short = ".".join(android_name.split(".")[:2])  # Android says 1.25.0, iPhone and README say 1.25

if ios_names != {short}:
    fail(f"Version: Android is {android_name}, iPhone is {sorted(ios_names)}. Both must be {short}.")
if len(ios_builds) != 1:
    fail(f"Version: the iPhone targets have different build numbers {sorted(ios_builds)}.")
newest = re.search(r"^## What's new in ([\d.]+)", read("README.md"), re.M)
if not newest or newest.group(1) != short:
    fail(f"README: the newest \"What's new\" is {newest.group(1) if newest else 'missing'}, the app is {short}.")
notes.append(f"Version {short}, Android version code {android_code}, iPhone build {', '.join(sorted(ios_builds))}")

# 2. Store text -------------------------------------------------------------------------
langs = sorted(p.stem for p in (ROOT / "lang").glob("*.xml"))
# File name -> the most characters the store takes.
PLAY = {"title.txt": 30, "short-description.txt": 80, "full-description.txt": 4000, "release-notes.txt": 500}
APPLE = {"name.txt": 30, "subtitle.txt": 30, "keywords.txt": 100, "promotional-text.txt": 170,
         "description.txt": 4000, "release-notes.txt": 4000}


def store(folder, limits):
    """Checks one store's listing folder. Returns {language code: release notes}."""
    out = {}
    dirs = sorted(p for p in (ROOT / folder).iterdir() if p.is_dir())
    have = {d.name.split("-")[0] for d in dirs}
    for code in langs:
        if code not in have:
            fail(f"{folder}: no folder for the language \"{code}\".")
    for d in dirs:
        for name, limit in limits.items():
            f = d / name
            if not f.exists():
                fail(f"{folder}/{d.name}: {name} is missing.")
                continue
            text = f.read_text(encoding="utf-8").strip()
            if not text:
                fail(f"{folder}/{d.name}/{name} is empty.")
            if len(text) > limit:
                fail(f"{folder}/{d.name}/{name} is {len(text)} characters, the store takes {limit}.")
            if name == "release-notes.txt":
                out[d.name.split("-")[0]] = text
    return out


# Release notes may differ between the stores, when a fix touched only one app.
store("play-store/listing", PLAY)
store("app-store/listing", APPLE)
notes.append(f"Store text in {len(langs)} languages on both stores")

# 3. Android permissions ----------------------------------------------------------------
# The privacy policy and both stores say no data leaves the phone. A new permission here
# (INTERNET above all) breaks that promise, so adding one must be a choice, made in this list.
ALLOWED = {
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_MEDIA_LOCATION",
    "android.permission.POST_NOTIFICATIONS",
}
ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"
manifest = ET.parse(ROOT / "android/app/src/main/AndroidManifest.xml").getroot()
asked = set()
for p in manifest.iter("uses-permission"):
    if p.get(TOOLS + "node") != "remove":
        asked.add(p.get(ANDROID + "name"))
for name in sorted(asked - ALLOWED):
    fail(f"Android asks for {name}, which is not on the allowed list in .github/qa/release_check.py.")
notes.append(f"{len(asked)} Android permissions, all on the allowed list")

# 4. Words the code asks for -------------------------------------------------------------
en = ET.parse(ROOT / "lang/en.xml").getroot()
strings = {e.get("name") for e in en.iter("string")}
plurals = {e.get("name") for e in en.iter("plurals")}
call = re.compile(r'\bL\.(s|p)\(\s*"([A-Za-z0-9_]+)"')
quoted = re.compile(r'"([a-z][a-z0-9_]+)"')
used = set()
sources = [p for d in ("android/app/src/main", "ios/BackThen", "ios/BackThenWidget")
           for p in (ROOT / d).rglob("*") if p.suffix in (".kt", ".swift")]
for path in sources:
    text = path.read_text(encoding="utf-8")
    for line_no, line in enumerate(text.splitlines(), 1):
        for kind, key in call.findall(line):
            if key not in (strings if kind == "s" else plurals):
                fail(f"{path.relative_to(ROOT)}:{line_no} asks for \"{key}\", which lang/en.xml does not have.")
    # Keys also reach L.s through variables, so any quoted key name counts as used.
    used |= set(quoted.findall(text))
unused = sorted((strings | plurals) - used)
if unused:
    notes.append(f"{len(unused)} words in lang/en.xml that no code names (may be built from parts): "
                 + ", ".join(unused[:12]) + ("..." if len(unused) > 12 else ""))
notes.append(f"{len(strings)} words and {len(plurals)} plurals checked against {len(sources)} source files")

# 5. City list ids --------------------------------------------------------------------------
# Saved places point at cities by row number, so rows on main must stay as they are: new
# cities go at the end. Compares with main when it has been fetched (origin/main).
import subprocess

old = subprocess.run(["git", "-C", str(ROOT), "show", "origin/main:places/cities.tsv"],
                     capture_output=True, text=True)
if old.returncode == 0:
    old_rows = old.stdout.splitlines()
    new_rows = read("places/cities.tsv").splitlines()
    changed = next((i for i, (a, b) in enumerate(zip(old_rows, new_rows)) if a != b), None)
    if changed is not None or len(new_rows) < len(old_rows):
        row = changed + 1 if changed is not None else len(new_rows) + 1
        fail(f"places/cities.tsv: row {row} differs from main. Add new cities at the end only, "
             "or saved places will name the wrong city.")
    else:
        notes.append(f"City list keeps all {len(old_rows)} rows from main")
else:
    notes.append("City list not compared (origin/main not fetched)")

# ---------------------------------------------------------------------------------------
for n in notes:
    print("ok  ", n)
if problems:
    print()
    for p in problems:
        print("FAIL", p)
    print(f"\n{len(problems)} problem(s). Fix them before this build goes to a store.")
    sys.exit(1)
print("\nQuick release checks passed.")

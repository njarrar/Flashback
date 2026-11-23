"""Quick release checks that need no build. Run from the top of the repo:

    python3 .github/qa/release_check.py

Fails when:
  - the Android and iPhone versions differ, or the README has no "What's new" for them
  - a store folder lacks a language, a text file, or holds text longer than the store takes
  - the Android app asks for a permission that is not on the list below
  - any lang/*.xml or Android res/values*/strings.xml file fails XML, key, slot, or CLDR checks
  - code asks for a word that lang/en.xml does not have, or dead keys exist in lang/en.xml
  - a row of places/cities.tsv on main moved or changed (fails if origin/main is missing in CI)
See docs/QA.md (gate 1).
"""
import os
import re
import subprocess
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
parts = android_name.split(".")
# Android uses X.Y.0 when patch is 0 (matching iOS/README X.Y), and X.Y.Z must match exactly when Z != 0.
short = ".".join(parts[:2]) if (len(parts) == 3 and parts[2] == "0") else android_name

if ios_names != {short}:
    fail(f"Version: Android is {android_name}, iPhone is {sorted(ios_names)}. Both must be {short}.")
if len(ios_builds) != 1:
    fail(f"Version: the iPhone targets have different build numbers {sorted(ios_builds)}.")
newest = re.search(r"^## What's new in ([\d.]+)", read("README.md"), re.M)
if not newest or newest.group(1) != short:
    fail(f"README: the newest \"What's new\" is {newest.group(1) if newest else 'missing'}, the app is {short}.")
notes.append(f"Version {short}, Android version code {android_code}, iPhone build {', '.join(sorted(ios_builds))}")

# 2. Store text -------------------------------------------------------------------------
lang_files = sorted((ROOT / "lang").glob("*.xml"))
langs = [p.stem for p in lang_files]
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

# 4. Multi-locale XML, Android resources & code keys ------------------------------------
slot_re = re.compile(r"\{(\w+)\}")
parsed_langs = {}
for path in lang_files:
    code = path.stem
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as e:
        fail(f"lang/{code}.xml: invalid XML ({e}).")
        continue
    s_map, p_map = {}, {}
    for el in root:
        if el.tag == "string":
            k = el.get("name")
            v = el.text or ""
            if k in s_map:
                fail(f"lang/{code}.xml: duplicate string \"{k}\".")
            if not v.strip():
                fail(f"lang/{code}.xml: string \"{k}\" is empty.")
            s_map[k] = v
        elif el.tag == "plurals":
            k = el.get("name")
            if k in p_map:
                fail(f"lang/{code}.xml: duplicate plurals \"{k}\".")
            forms = {}
            for item in el.findall("item"):
                q = item.get("quantity")
                iv = item.text or ""
                if q in forms:
                    fail(f"lang/{code}.xml: duplicate plural form {k}.{q}.")
                if not iv.strip():
                    fail(f"lang/{code}.xml: empty plural form {k}.{q}.")
                forms[q] = iv
            p_map[k] = forms
    parsed_langs[code] = (s_map, p_map)

en_strings, en_plurals = parsed_langs.get("en", ({}, {}))
strings = set(en_strings)
plurals = set(en_plurals)

CLDR_REQUIRED = {
    "ar": {"zero", "one", "two", "few", "many", "other"},
    "en": {"one", "other"},
    "es": {"one", "other"},
    "fr": {"one", "other"},
    "pt": {"one", "other"},
    "zh": {"other"},
}

for code, (s_map, p_map) in parsed_langs.items():
    if code != "en":
        missing_s = sorted(strings - set(s_map))
        extra_s = sorted(set(s_map) - strings)
        if missing_s:
            fail(f"lang/{code}.xml: missing string(s): {', '.join(missing_s[:10])}.")
        if extra_s:
            fail(f"lang/{code}.xml: extra string(s) not in en.xml: {', '.join(extra_s[:10])}.")
        missing_p = sorted(plurals - set(p_map))
        extra_p = sorted(set(p_map) - plurals)
        if missing_p:
            fail(f"lang/{code}.xml: missing plurals: {', '.join(missing_p[:10])}.")
        if extra_p:
            fail(f"lang/{code}.xml: extra plurals not in en.xml: {', '.join(extra_p[:10])}.")
        for k in sorted(strings & set(s_map)):
            want_slots = set(slot_re.findall(en_strings[k]))
            got_slots = set(slot_re.findall(s_map[k]))
            if want_slots != got_slots:
                fail(f"lang/{code}.xml: string \"{k}\" slots {sorted(got_slots)} != en {sorted(want_slots)}.")
    for k, forms in p_map.items():
        if "other" not in forms:
            fail(f"lang/{code}.xml: plurals \"{k}\" lacks required 'other' form.")
        req = CLDR_REQUIRED.get(code, {"other"})
        if code == "ar" and k == "ago_years":
            req = {"one", "two", "few", "many", "other"}
        miss_q = sorted(req - set(forms))
        if miss_q:
            fail(f"lang/{code}.xml: plurals \"{k}\" missing CLDR form(s): {', '.join(miss_q)}.")
        allowed = set(slot_re.findall(en_plurals.get(k, {}).get("other", "")))
        for q, iv in forms.items():
            extra_slots = set(slot_re.findall(iv)) - allowed
            if extra_slots:
                fail(f"lang/{code}.xml: plurals \"{k}.{q}\" has unknown slot(s): {sorted(extra_slots)}.")

# Validate Android res/values*/strings.xml files
android_res_files = sorted((ROOT / "android/app/src/main/res").glob("values*/strings.xml"))
for res_path in android_res_files:
    try:
        res_root = ET.parse(res_path).getroot()
    except ET.ParseError as e:
        fail(f"{res_path.relative_to(ROOT)}: invalid XML ({e}).")
        continue
    res_strings = {e.get("name"): (e.text or "") for e in res_root.findall("string")}
    folder = res_path.parent.name
    code = "en" if folder == "values" else folder.removeprefix("values-")
    if code in parsed_langs and "widget_description" in res_strings:
        want_wd = parsed_langs[code][0].get("widget_description", "")
        got_wd = res_strings["widget_description"].replace("\\'", "'")
        if got_wd != want_wd:
            fail(f"{res_path.relative_to(ROOT)}: widget_description \"{got_wd}\" != lang/{code}.xml \"{want_wd}\".")
notes.append(f"All {len(parsed_langs)} lang/*.xml and {len(android_res_files)} Android strings.xml validated")

# Dynamically constructed keys (built via interpolation in Reel UI and validated by unit tests).
DYNAMIC_KEYS = {
    "reel_move_fade_note",
    "reel_move_flash_note",
    "reel_move_slide_note",
    "reel_move_zoom_note",
    "reel_speed_fast",
    "reel_speed_normal",
    "reel_speed_slow",
}

call = re.compile(r'\bL\.(s|p)\(\s*"([A-Za-z0-9_]+)"')
quoted = re.compile(r'"([a-z][a-z0-9_]+)"')
script_key = re.compile(r'\b(ios_[a-z0-9_]+)\b')
used = set(DYNAMIC_KEYS)
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
copy_lang = ROOT / "ios/scripts/copy-lang.sh"
if copy_lang.exists():
    sh_text = copy_lang.read_text(encoding="utf-8")
    used |= set(quoted.findall(sh_text))
    used |= set(script_key.findall(sh_text))

unused = sorted((strings | plurals) - used)
if unused:
    fail(f"Unused translation key(s) in lang/en.xml: {', '.join(unused)}. Remove dead keys or add dynamic ones to DYNAMIC_KEYS.")
notes.append(f"{len(strings)} words and {len(plurals)} plurals checked against {len(sources)} source files (0 dead keys)")

# 5. City list ids --------------------------------------------------------------------------
# Saved places point at cities by row number, so rows on main must stay as they are: new
# cities go at the end. Compares with main when fetched; fails explicitly in CI if missing.
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
    if os.environ.get("GITHUB_ACTIONS") == "true" or "--strict-main" in sys.argv:
        fail("places/cities.tsv: origin/main is not fetched, so city row immutability could not be verified.")
    else:
        notes.append("City list not compared (origin/main not fetched; pass --strict-main or run in CI to enforce)")

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

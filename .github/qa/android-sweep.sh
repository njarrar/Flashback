#!/usr/bin/env bash
# Release QA gate 3 (docs/QA.md): the screen sweep on one emulator. Opens every screen of the
# QA build (the release build, shrunk by R8, plus the scene launch options) in every language,
# then in dark mode in English and Arabic. On each screen it saves a picture and fails the run
# when the screen shows a raw text key or draws nothing. It also fails on any crash or freeze.
set -ex
sleep 30
PKG=com.njarrar.backthen
API=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
OUT=shots/sweep/api$API
mkdir -p "$OUT"
QA_DIR=$OUT
source .github/qa/screen-text.sh

adb install -r android/app/build/outputs/apk/qa/app-qa.apk
adb shell mkdir -p /sdcard/DCIM/Camera
for f in /tmp/photos/*.jpg; do adb push "$f" /sdcard/DCIM/Camera/ >/dev/null; done
if [ "$API" -ge 29 ]; then
  adb shell content call --uri content://media/ --method scan_volume --arg external_primary || true
else
  for f in /tmp/photos/*.jpg; do
    adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/DCIM/Camera/$(basename "$f")" >/dev/null || true
  done
fi
sleep 5

grant() {
  if [ "$API" -ge 33 ]; then
    adb shell pm grant $PKG android.permission.READ_MEDIA_IMAGES
    adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
  else
    adb shell pm grant $PKG android.permission.READ_EXTERNAL_STORAGE
  fi
}

launch() { # label extra_args...
  local label=$1; shift
  if ! timeout 30 adb shell am start -W -n $PKG/.MainActivity "$@"; then
    echo "api$API/$label: activity launch failed or timed out" >> "$QA_DIR/problems.txt"
  fi
}

shot() { # label
  local file="$OUT/$1.png"
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
  sleep 1
  adb exec-out screencap -p > "$file"
  qa_check "api$API/$1"
  /tmp/venv/bin/python .github/qa/screen_image.py "$file" "api$API/$1" "$QA_DIR/problems.txt"
}

# A crash or "isn't responding" since the last look fails the run.
health() { # label
  local log="$OUT/crash-$1.txt"
  { adb logcat -d -b crash; adb logcat -d | grep "ANR in $PKG"; } > "$log" 2>/dev/null || true
  if grep -q "Process: $PKG\|ANR in $PKG" "$log"; then
    echo "api$API/$1: crash or freeze, see crash-$1.txt" >> "$QA_DIR/problems.txt"
  else
    rm -f "$log"
  fi
  adb logcat -b all -c || true
}

LANGS=$(ls lang/*.xml | xargs -n1 basename | sed 's/\.xml$//' | tr '\n' ' ')
SCENES=$(cat .github/text-check/scenes.txt)

sweep() { # mode languages...
  local mode=$1; shift
  if [ "$mode" = dark ]; then adb shell cmd uimode night yes || true; else adb shell cmd uimode night no || true; fi
  for l in "$@"; do
    # Capture Step 1 Welcome BEFORE granting photo access so onboarding does not auto-skip to Step 2.
    adb shell pm clear $PKG >/dev/null || true
    adb logcat -b all -c || true
    launch "$mode-$l-welcome" --es lang "$l"
    sleep 6
    shot "$mode-$l-welcome"
    # Grant permissions only after Step 1 Welcome is verified.
    grant
    for scene in $SCENES; do
      adb shell am force-stop $PKG
      launch "$mode-$l-$scene" --es scene "$scene" --es lang "$l"
      sleep 8
      shot "$mode-$l-$scene"
    done
    health "$mode-$l"
  done
}

sweep light $LANGS
sweep dark en ar
adb shell cmd uimode night no || true
qa_finish

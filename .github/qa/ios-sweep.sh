#!/usr/bin/env bash
# Release QA gate 3 (docs/QA.md) on the iPhone simulator. Opens every screen of the QA build
# (Release, optimized, plus the -scene launch options) in every language, then in dark mode
# in English and Arabic. Fails when a screen shows a raw text key (read from the screenshot),
# draws nothing, or the app crashes.
#
# Usage: ios-sweep.sh "<device type>" "<runtime, such as iOS 18>" <folder name>
set -ex
TYPE=$1; RUNTIME_NAME=$2; NAME=$3
APP=com.njarrar.backthen
BUNDLE="/tmp/dd/Build/Products/Release-iphonesimulator/Back Then.app"
OUT="shots/sweep/$NAME"; mkdir -p "$OUT"
PROBLEMS="$OUT/problems.txt"; : > "$PROBLEMS"

RUNTIME=$(xcrun simctl list runtimes available | grep "^$RUNTIME_NAME" | tail -1 | grep -oE "com\.apple\.CoreSimulator\.SimRuntime\.[A-Za-z0-9-]+")
[ -n "$RUNTIME" ] || { xcrun simctl list runtimes; echo "::error::No $RUNTIME_NAME simulator on this machine"; exit 1; }
DEVICE=$(xcrun simctl create "qa-$NAME" "$TYPE" "$RUNTIME")
xcrun simctl boot "$DEVICE"
xcrun simctl bootstatus "$DEVICE" -b
xcrun simctl status_bar "$DEVICE" override --time "9:41" --batteryState charged --batteryLevel 100 || true

CRASHES=~/Library/Logs/DiagnosticReports
mkdir -p "$CRASHES"
touch /tmp/sweep-start

fresh() {
  xcrun simctl uninstall "$DEVICE" $APP || true
  xcrun simctl install "$DEVICE" "$BUNDLE"
  DATA=$(xcrun simctl get_app_container "$DEVICE" $APP data)
  mkdir -p "$DATA/Library/Application Support/samples"
  cp /tmp/photos/*.jpg "$DATA/Library/Application Support/samples/"
}
apple_lang() { case $1 in zh) echo zh-Hans;; pt) echo pt-BR;; *) echo "$1";; esac; }
apple_locale() { case $1 in ar) echo ar_SA;; zh) echo zh_CN;; pt) echo pt_BR;; fr) echo fr_FR;; es) echo es_ES;; *) echo en_US;; esac; }
shot() {
  xcrun simctl io "$DEVICE" screenshot "$OUT/$1.png"
}

LANGS=$(ls lang/*.xml | xargs -n1 basename | sed 's/\.xml$//')
SCENES=$(cat .github/text-check/scenes.txt)
sweep() { # mode languages...
  local mode=$1; shift
  xcrun simctl ui "$DEVICE" appearance "$mode"
  for l in "$@"; do
    fresh
    ARGS=(-AppleLanguages "($(apple_lang $l))" -AppleLocale "$(apple_locale $l)")
    xcrun simctl terminate "$DEVICE" $APP || true
    xcrun simctl launch "$DEVICE" $APP "${ARGS[@]}"
    sleep 8
    shot "$mode-$l-welcome"
    for scene in $SCENES; do
      xcrun simctl terminate "$DEVICE" $APP || true
      xcrun simctl launch "$DEVICE" $APP "${ARGS[@]}" -scene "$scene"
      sleep 9
      shot "$mode-$l-$scene"
    done
  done
}
sweep light $LANGS
sweep dark en ar
xcrun simctl shutdown "$DEVICE" || true
xcrun simctl delete "$DEVICE" || true

# Crash reports written during the sweep (the app's binary is named "Back Then").
find "$CRASHES" -newer /tmp/sweep-start -name "Back Then*" -print | while read -r f; do
  cp "$f" "$OUT/"
  echo "$NAME: the app crashed, see $(basename "$f")" >> "$PROBLEMS"
done

# Read the words on every screenshot, then check them and the pictures.
swift .github/qa/ocr.swift "$OUT"/*.png
python3 .github/qa/raw_keys_text.py lang/en.xml "$PROBLEMS" "$OUT"/*.txt
for f in "$OUT"/*.png; do /tmp/venv/bin/python .github/qa/screen_image.py "$f" "$NAME/$(basename "$f" .png)" "$PROBLEMS"; done

if [ -s "$PROBLEMS" ]; then
  echo "::error::The iPhone screen sweep found problems:"
  cat "$PROBLEMS"
  exit 1
fi
echo "iPhone screen sweep on $NAME: $(ls "$OUT"/*.png | wc -l) screens, no problems."

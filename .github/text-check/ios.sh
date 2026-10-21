#!/usr/bin/env bash
# Usage: text-check-ios.sh <device name pattern> <content size> <folder>
# Shoots every scene in English and Arabic at one Dynamic Type size.
set -ex
DEVICE=$(xcrun simctl list devices available | grep -E "$1 \(" | head -1 | grep -oE "[0-9A-F-]{36}")
[ -n "$DEVICE" ] || { xcrun simctl list devices available; exit 1; }
xcrun simctl boot "$DEVICE"
xcrun simctl bootstatus "$DEVICE" -b
xcrun simctl ui "$DEVICE" content_size "$2"
xcrun simctl status_bar "$DEVICE" override --time "9:41" --batteryState charged --batteryLevel 100 --cellularBars 4 --wifiBars 3
APP=com.njarrar.backthen
# A fresh install per language, so the welcome screen shows instead of the photo prompt.
fresh() {
  xcrun simctl uninstall "$DEVICE" $APP || true
  xcrun simctl install "$DEVICE" "/tmp/dd/Build/Products/Debug-iphonesimulator/Back Then.app"
  DATA=$(xcrun simctl get_app_container "$DEVICE" $APP data)
  mkdir -p "$DATA/Library/Application Support/samples"
  cp /tmp/photos/*.jpg "$DATA/Library/Application Support/samples/"
}
OUT="shots/$3"
mkdir -p "$OUT"
SCENES=$(cat .github/text-check/scenes.txt)
for lang in en ar; do
  fresh
  if [ $lang = ar ]; then ARGS="-AppleLanguages (ar) -AppleLocale ar_SA"; else ARGS="-AppleLanguages (en) -AppleLocale en_US"; fi
  # Welcome first, before a demo scene marks the app as set up.
  xcrun simctl terminate "$DEVICE" $APP || true
  xcrun simctl launch "$DEVICE" $APP $ARGS
  sleep 8
  xcrun simctl io "$DEVICE" screenshot "$OUT/$lang-welcome.png"
  for scene in $SCENES; do
    xcrun simctl terminate "$DEVICE" $APP || true
    xcrun simctl launch "$DEVICE" $APP $ARGS -scene $scene
    sleep 9
    xcrun simctl io "$DEVICE" screenshot "$OUT/$lang-$scene.png"
  done
done
xcrun simctl shutdown "$DEVICE"

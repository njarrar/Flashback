#!/usr/bin/env bash
# Records the screens for the video ad (ad/) on the newest Pro Max simulator.
# Usage: capture.sh "en fr es ar pt zh"
# For each language and scene it keeps a screen recording (<scene>.mp4) and a
# screenshot taken at its end (<scene>.png), in shots/<lang>/.
set -ex
LANGS=${1:-en fr es ar pt zh}
DEVICE=$(xcrun simctl list devices available | grep -E "iPhone [0-9]+ Pro Max \(" | sort -V | tail -1 | grep -oE "[0-9A-F-]{36}")
[ -n "$DEVICE" ] || { xcrun simctl list devices available; exit 1; }
xcrun simctl boot "$DEVICE"
xcrun simctl bootstatus "$DEVICE" -b
xcrun simctl status_bar "$DEVICE" override --time "9:41" --batteryState charged --batteryLevel 100 --cellularBars 4 --wifiBars 3
APP=com.njarrar.backthen
BUILT="/tmp/dd/Build/Products/Debug-iphonesimulator/Back Then.app"
locale() { case $1 in en) echo en_US;; fr) echo fr_FR;; es) echo es_ES;; ar) echo ar_SA;; pt) echo pt_BR;; zh) echo zh_CN;; esac; }
plang() { case $1 in pt) echo pt-BR;; zh) echo zh-Hans;; *) echo $1;; esac; }
# A fresh install per language, so the welcome screen shows. The debug demo reads the
# sample photos from the app's samples folder (see Demo.swift).
fresh() {
  xcrun simctl uninstall "$DEVICE" $APP || true
  xcrun simctl install "$DEVICE" "$BUILT"
  DATA=$(xcrun simctl get_app_container "$DEVICE" $APP data)
  mkdir -p "$DATA/Library/Application Support/samples"
  cp /tmp/photos/*.jpg "$DATA/Library/Application Support/samples/"
}
# record <name> <launch args...>: films the launch and 14 seconds after it. The ad
# uses the last 4.5 seconds, once the scene has settled.
record() {
  local out="$OUT/$1.mp4"
  shift
  xcrun simctl terminate "$DEVICE" $APP || true
  xcrun simctl io "$DEVICE" recordVideo --codec=h264 --force "$out" &
  local rec=$!
  sleep 2
  xcrun simctl launch "$DEVICE" $APP "$@"
  sleep 14
  xcrun simctl io "$DEVICE" screenshot "${out%.mp4}.png"
  kill -INT $rec
  wait $rec || true
  ls -l "$out"
}
mkdir -p shots
date +%F > shots/date.txt
for lang in $LANGS; do
  OUT="shots/$lang"
  mkdir -p "$OUT"
  fresh
  ARGS=(-AppleLanguages "($(plang $lang))" -AppleLocale "$(locale $lang)")
  # Welcome first, before a demo scene marks the app as set up.
  record welcome "${ARGS[@]}"
  for scene in today calendar viewer favorites album select cards reel; do
    record $scene "${ARGS[@]}" -scene $scene
  done
done
xcrun simctl shutdown "$DEVICE"

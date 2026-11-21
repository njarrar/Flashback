#!/usr/bin/env bash
# Records the screens for the video ad (ad/) on the newest Pro Max simulator.
# Usage: capture.sh "en fr es ar pt zh"
# For each language and scene it keeps a screen recording (<scene>.mp4) and a
# screenshot taken at its end (<scene>.png), in shots/<lang>/.
set -ex
LANGS=${1:-en fr es ar pt zh}
APP=com.njarrar.backthen
# limit <seconds> <command...>: runs a command, and kills it if it takes longer. A
# simulator call that hangs once stalled a whole run for an hour.
limit() {
  local secs=$1
  shift
  "$@" &
  local pid=$!
  ( sleep "$secs"; kill -9 $pid 2>/dev/null && echo "::warning::Stopped after ${secs}s: $*" ) &
  local guard=$!
  local rc=0
  wait $pid || rc=$?
  kill $guard 2>/dev/null || true
  return $rc
}
# Some runners never finish booting a given simulator, so try each Pro Max, newest first,
# with four minutes each, and use the first that boots.
DEVICE=""
for d in $(xcrun simctl list devices available | grep -E "iPhone [0-9]+ Pro Max \(" | sort -rV | grep -oE "[0-9A-F-]{36}"); do
  xcrun simctl boot "$d" || true
  if limit 240 xcrun simctl bootstatus "$d" -b; then DEVICE=$d; break; fi
  echo "::warning::Simulator $d did not boot; trying the next one."
  xcrun simctl shutdown "$d" || true
done
[ -n "$DEVICE" ] || { xcrun simctl list devices available; echo "::error::No Pro Max simulator booted."; exit 1; }
xcrun simctl status_bar "$DEVICE" override --time "9:41" --batteryState charged --batteryLevel 100 --cellularBars 4 --wifiBars 3
BUILT="/tmp/dd/Build/Products/Debug-iphonesimulator/Back Then.app"
locale() { case $1 in en) echo en_US;; fr) echo fr_FR;; es) echo es_ES;; ar) echo ar_SA;; pt) echo pt_BR;; zh) echo zh_CN;; esac; }
plang() { case $1 in pt) echo pt-BR;; zh) echo zh-Hans;; *) echo $1;; esac; }
# A fresh install per language, so the welcome screen shows. The debug demo reads the
# sample photos from the app's samples folder (see Demo.swift).
fresh() {
  limit 60 xcrun simctl uninstall "$DEVICE" $APP || true
  limit 120 xcrun simctl install "$DEVICE" "$BUILT"
  DATA=$(xcrun simctl get_app_container "$DEVICE" $APP data)
  mkdir -p "$DATA/Library/Application Support/samples"
  cp /tmp/photos/*.jpg "$DATA/Library/Application Support/samples/"
}
# record <name> <launch args...>: films the launch and 14 seconds after it. The ad
# uses the last 4.5 seconds, once the scene has settled.
record() {
  local out="$OUT/$1.mp4"
  shift
  limit 30 xcrun simctl terminate "$DEVICE" $APP || true
  xcrun simctl io "$DEVICE" recordVideo --codec=h264 --force "$out" &
  local rec=$!
  sleep 2
  limit 60 xcrun simctl launch "$DEVICE" $APP "$@" || true
  sleep 14
  limit 30 xcrun simctl io "$DEVICE" screenshot "${out%.mp4}.png" || true
  kill -INT $rec 2>/dev/null || true
  # The recorder finishes the file on SIGINT; if it doesn't stop, kill it.
  for _ in $(seq 1 20); do kill -0 $rec 2>/dev/null || break; sleep 1; done
  kill -9 $rec 2>/dev/null || true
  wait $rec 2>/dev/null || true
  ls -l "$out" || true
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

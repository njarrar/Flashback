#!/usr/bin/env bash
# Runs inside the emulator step: loads sample photos, then captures each scene.
set -ex
PKG=com.njarrar.backthen
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell mkdir -p /sdcard/DCIM/Camera
for f in /tmp/photos/*.jpg; do adb push "$f" /sdcard/DCIM/Camera/ >/dev/null; done
adb shell content call --uri content://media/ --method scan_volume --arg external_primary || true
for f in /tmp/photos/*.jpg; do
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/DCIM/Camera/$(basename "$f")" >/dev/null || true
done
sleep 5
adb shell content query --uri content://media/external/images/media --projection _display_name:datetaken | head -5 || true
adb shell pm grant $PKG android.permission.READ_MEDIA_IMAGES
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
# Clean status bar.
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
mkdir -p shots/android
adb shell am start -W -n $PKG/.MainActivity
sleep 6
adb exec-out screencap -p > shots/android/1-welcome.png
for scene in today calendar viewer favorites album; do
  adb shell am force-stop $PKG
  adb shell am start -W -n $PKG/.MainActivity --es scene $scene
  sleep 8
  adb exec-out screencap -p > "shots/android/$scene.png"
done
